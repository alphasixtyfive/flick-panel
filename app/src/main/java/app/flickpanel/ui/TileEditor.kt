package app.flickpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SetupSearchField(query, changeQuery, "Find a device or sensor", Modifier.weight(1f))
            IconButton(onClick = refresh, enabled = !state.loadingCatalogue && state.link == Link.ONLINE) {
                Icon(Icons.Rounded.Refresh, "Refresh devices")
            }
        }
        if (source == SetupPicker.TILE) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (choice in DeviceFilter.entries) {
                    val selected = filter == choice
                    FilterChip(selected = selected, onClick = { filter = choice }, label = { Text(choice.label) },
                        leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                        shape = CircleShape)
                }
            }
        }
        if (state.loadingCatalogue) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (matches.isEmpty() && !state.loadingCatalogue) {
            val message = when {
                state.link != Link.ONLINE -> "Connect to Home Assistant to find devices"
                query.isNotBlank() -> "No results for this search"
                else -> "No devices in this category"
            }
            Text(message, Modifier.padding(start = 16.dp, top = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(matches, key = { it.entity.id }) { entry ->
                val entity = entry.entity
                ListItem(
                    headlineContent = { Text(entity.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(areas[entry.area] ?: entity.id,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(entityIcon(entity.domain), null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button) {
                        focus.clearFocus()
                        onSelect(entity)
                    },
                )
            }
        }
    }
}

private val searchWords = Regex("\\s+")

@Composable
internal fun SetupSearchField(query: String, change: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    TextField(query, { change(it.take(120)) },
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = hint },
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { change("") }) { Icon(Icons.Rounded.Close, "Clear search") }
        }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHighest, unfocusedContainerColor = colors.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

private fun entityIcon(domain: String) = when (domain) {
    "light" -> Icons.Rounded.Lightbulb
    "switch", "input_boolean" -> Icons.Rounded.PowerSettingsNew
    "climate" -> Icons.Rounded.Thermostat
    "camera" -> Icons.Rounded.Videocam
    "media_player" -> Icons.Rounded.MusicNote
    "calendar" -> Icons.Rounded.CalendarMonth
    "todo" -> Icons.Rounded.Checklist
    "person" -> Icons.Rounded.Person
    "button", "input_button", "scene", "script" -> Icons.Rounded.ElectricBolt
    else -> Icons.Rounded.Sensors
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormatPanel(format: TileFormat, state: PanelUi, apply: (TileSpec) -> Unit) {
    var kind by remember(format) { mutableStateOf(format.current?.kind ?: format.kinds.first()) }
    var label by remember(format) { mutableStateOf(format.current?.label.orEmpty()) }
    var status by remember(format) { mutableStateOf(format.current?.statusEntity) }
    var showOnHome by remember(format) { mutableStateOf(format.current?.showOnHome ?: format.suggestedOnHome) }
    var wideOnTablet by remember(format) { mutableStateOf(format.current?.wideOnTablet ?: false) }
    var cameraGate by remember(format) { mutableStateOf(format.current?.cameraGate) }
    var cameraStream by remember(format) { mutableStateOf(format.current?.cameraStream) }
    var confirmAction by remember(format) { mutableStateOf(format.current?.confirmAction ?: false) }
    var readerAttribute by remember(format) { mutableStateOf(format.current?.readerAttribute) }
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = setupPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val preview = state.entity(format.entity.id) ?: format.entity
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
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(entityIcon(preview.domain), null) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(format.entity.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(label, { label = it.take(72) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Tile name") }, placeholder = { Text(format.entity.name, maxLines = 1) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
            if (format.kinds.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tile type", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in format.kinds) FilterChip(selected = kind == option, shape = CircleShape,
                            onClick = { kind = option }, label = { Text(option.label) })
                    }
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
                val streams = remember(state.catalogue, format.entity.id) {
                    state.catalogue.entries.map { it.entity }.filter {
                        it.domain == "camera" && it.id != format.entity.id && it.attributes.optLong("supported_features") and 2L != 0L
                    }.sortedBy { it.name.lowercase() }
                }
                LinkedEntitySetting("Live stream source", cameraStream, streams, state, defaultLabel = "This camera") {
                    cameraStream = it
                }
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
                    cameraStream = cameraStream.takeIf { kind == TileKind.CAMERA },
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
    title: String, selected: String?, options: List<Entity>, state: PanelUi, defaultLabel: String = "None",
    change: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = selected?.let { state.entity(it)?.name ?: it } ?: defaultLabel
    Box {
        SettingLink(title, description, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { open = false; change(null) })
            for (option in options) {
                DropdownMenuItem(text = { Text(option.name) }, onClick = { open = false; change(option.id) })
            }
        }
    }
}
