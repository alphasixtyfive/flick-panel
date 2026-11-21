package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.validBadgeSource
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link
import app.flickpanel.ha.readerAttributes

internal enum class SetupPicker(val title: String) {
    TILE("Add tile"), APP("Add tile"), MESSAGE("Choose a message"), PRESENCE("Choose a presence sensor"),
    BADGE("Choose a warning sensor"),
}

internal data class TileFormat(
    val entity: Entity, val current: TileSpec?, val kinds: List<TileKind>, val suggestedOnHome: Boolean = true,
)

private enum class DeviceFilter(val label: String, private vararg val domains: String) {
    ALL("All"),
    CONTROLS("Controls", "light", "switch", "input_boolean", "climate", "input_select", "select", "number", "input_number", "cover"),
    SENSORS("Sensors", "sensor", "binary_sensor", "timer", "input_text", "weather", "image", "person"),
    CAMERAS("Cameras", "camera"),
    MEDIA("Media", "media_player"),
    AGENDA("Tasks & calendar", "todo", "calendar"),
    ACTIONS("Actions", "button", "input_button", "scene", "script");

    fun shows(domain: String) = this == ALL || domain in domains
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntityPicker(
    source: SetupPicker, draft: PanelConfig, state: PanelUi, query: String, changeQuery: (String) -> Unit,
    refresh: () -> Unit, onSelect: (Entity) -> Unit,
) {
    var filter by rememberSaveable(source) { mutableStateOf(DeviceFilter.ALL) }
    val catalog = state.catalogue
    val areas = remember(catalog.areas) { catalog.areas.associate { it.id to it.name } }
    val available = remember(catalog, draft.favourites, draft.homeBadges, source, areas) {
        val present = draft.favourites.mapTo(hashSetOf()) { it.entity }
        val badges = draft.homeBadges.mapTo(hashSetOf()) { it.entity }
        catalog.entries.filter { entry -> when (source) {
            SetupPicker.TILE -> entry.entity.id !in present &&
                (entry.visible || entry.entity.available && entry.entity.domain in setOf("todo", "calendar"))
            SetupPicker.APP -> false
            SetupPicker.MESSAGE -> entry.visible && entry.entity.domain in setOf("sensor", "input_text")
            SetupPicker.PRESENCE -> entry.visible && entry.entity.domain == "binary_sensor"
            SetupPicker.BADGE -> entry.entity.id !in badges && validBadgeSource(entry.entity.id)
        } }.map { entry ->
            val entity = entry.entity
            entry to "${entity.name} ${entity.id} ${areas[entry.area].orEmpty()}".lowercase()
        }
    }
    val matches = remember(available, filter, query, source) {
        val words = query.trim().lowercase().split(searchWords).filter(String::isNotEmpty)
        available.mapNotNull { (entry, searchable) ->
            val include = source != SetupPicker.TILE ||
                filter.shows(entry.entity.domain) && (!entry.diagnostic || words.isNotEmpty())
            val explicitlyRequested = query.trim().equals(entry.entity.id, ignoreCase = true)
            entry.takeIf { include && (entry.visible || explicitlyRequested) && words.all { word -> searchable.contains(word) } }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(query, { changeQuery(it.take(120)) }, label = { Text("Find a device or sensor") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.weight(1f))
            IconButton(onClick = refresh, enabled = !state.loadingCatalogue && state.link == Link.ONLINE) {
                Icon(Icons.Rounded.Refresh, "Refresh devices")
            }
        }
        if (source == SetupPicker.TILE) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(top = 5.dp)) {
            for (choice in DeviceFilter.entries) {
                FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice.label) })
            }
        }
        if (state.loadingCatalogue) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        } else if (matches.isEmpty()) {
            val message = when {
                state.link != Link.ONLINE -> "Connect to Home Assistant to find devices"
                query.isNotBlank() -> "No results for this search"
                else -> "No devices in this category"
            }
            Text(message, Modifier.padding(vertical = 17.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(matches, key = { it.entity.id }) { entry ->
                val entity = entry.entity
                Surface(onClick = { onSelect(entity) }, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    ListItem(headlineContent = { Text(entity.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(areas[entry.area] ?: entity.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
    }
}

private val searchWords = Regex("\\s+")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormatPanel(format: TileFormat, state: PanelUi, apply: (TileSpec) -> Unit) {
    var kind by remember(format) { mutableStateOf(format.current?.kind ?: format.kinds.first()) }
    var label by remember(format) { mutableStateOf(format.current?.label.orEmpty()) }
    var status by remember(format) { mutableStateOf(format.current?.statusEntity) }
    var showOnHome by remember(format) { mutableStateOf(format.current?.showOnHome ?: format.suggestedOnHome) }
    var wideOnTablet by remember(format) { mutableStateOf(format.current?.wideOnTablet ?: false) }
    var cameraGate by remember(format) { mutableStateOf(format.current?.cameraGate) }
    var confirmAction by remember(format) { mutableStateOf(format.current?.confirmAction ?: false) }
    var readerAttribute by remember(format) { mutableStateOf(format.current?.readerAttribute) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(setupPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val preview = state.entity(format.entity.id) ?: format.entity
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label.trim().ifEmpty { format.entity.name }, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val example = when (kind) {
                        TileKind.APP -> "Open app"
                        TileKind.ACTION -> if (confirmAction) "Confirm to run" else "Run"
                        TileKind.CAMERA -> if (showOnHome) "Favourites and camera gallery" else "Camera gallery"
                    TileKind.READER -> "Open full-screen reader"
                    TileKind.PERSON -> "Open location in maps"
                        TileKind.TASKS -> "To-do list"
                        TileKind.CALENDAR -> "Upcoming events"
                        TileKind.COVER -> preview.coverStatus().takeUnless { it == "Unavailable" } ?: kind.label
                        else -> displayValue(preview).takeUnless { it == "Unavailable" } ?: kind.label
                    }
                    Text(example, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(label, { label = it.take(72) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Tile name") }, placeholder = { Text(format.entity.name) }, singleLine = true)
            if (format.kinds.size > 1) {
                Text("Tile type", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    for (option in format.kinds) FilterChip(selected = kind == option,
                        onClick = { kind = option }, label = { Text(option.label) })
                }
            }
            if (kind.resizable) {
                TileSizePicker(wideOnTablet) { wideOnTablet = it }
            }
            if (kind == TileKind.ACTION) {
                SetupSwitchRow("Confirm before running", null, confirmAction, { confirmAction = it })
                val timers = remember(state.catalogue) {
                    state.catalogue.entries.filter { it.visible && it.entity.domain == "timer" }.map { it.entity }
                }
                LinkedEntitySetting("Status timer", status, timers, state) { status = it }
            }
            if (kind == TileKind.CAMERA) {
                SetupSwitchRow("Pin to Favourites", null, showOnHome, { showOnHome = it })
                val gates = remember(state.catalogue) {
                    state.catalogue.entries.filter { it.visible && it.entity.gateWithOpenControl }.map { it.entity }
                }
                LinkedEntitySetting("Gate control", cameraGate, gates, state) { cameraGate = it }
            }
            if (kind == TileKind.READER) {
                val attributes = remember(preview.attributes) { preview.readerAttributes() }
                ReaderSource(readerAttribute, attributes) { readerAttribute = it }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = setupPadding, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                apply(TileSpec(
                    format.entity.id, kind, label.trim().takeIf(String::isNotEmpty), status.takeIf { kind == TileKind.ACTION },
                    showOnHome = kind != TileKind.CAMERA || showOnHome, wideOnTablet = kind.resizable && wideOnTablet,
                    cameraGate = cameraGate.takeIf { kind == TileKind.CAMERA },
                    confirmAction = kind == TileKind.ACTION && confirmAction,
                    readerAttribute = readerAttribute.takeIf { kind == TileKind.READER },
                ))
            }) { Text(if (format.current == null) "Add tile" else "Apply") }
        }
    }
}

@Composable
private fun ReaderSource(selected: String?, attributes: List<String>, change: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = selected?.replace('_', ' ') ?: "Entity state"
    Box {
        SettingLink("Text source", label, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Entity state") }, onClick = { open = false; change(null) })
            for (attribute in attributes) {
                DropdownMenuItem(text = { Text(attribute.replace('_', ' ')) },
                    onClick = { open = false; change(attribute) })
            }
        }
    }
}

@Composable
private fun LinkedEntitySetting(
    title: String, selected: String?, options: List<Entity>, state: PanelUi, change: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = selected?.let { state.entity(it)?.name ?: it } ?: "None"
    Box {
        SettingLink(title, description, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { open = false; change(null) })
            for (option in options) {
                DropdownMenuItem(text = { Text(option.name) }, onClick = { open = false; change(option.id) })
            }
        }
    }
}
