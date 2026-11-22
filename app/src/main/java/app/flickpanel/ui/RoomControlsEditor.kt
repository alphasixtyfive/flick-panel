package app.flickpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.RoomLayout
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.tileName
import java.util.Collections

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomControlsEditor(
    room: Area, draft: PanelConfig, state: PanelUi, selectedTile: String?, openTile: (TileSpec) -> Unit,
    update: (List<RoomTilePreference>) -> Unit, reset: () -> Unit, onInput: () -> Unit,
) {
    val controls = remember(room, draft, state.catalogue) { RoomLayout(draft, state.catalogue).controls(room) }
    val preferences = remember(controls) { controls.map { it.preference } }
    val scroll = rememberLazyListState()
    fun edit(preference: RoomTilePreference) {
        update(preferences.map { if (it.entity == preference.entity) preference else it })
    }
    if (selectedTile != null) {
        val control = controls.firstOrNull { it.preference.entity == selectedTile }
        val tile = control?.tile
        if (control != null && tile != null) {
            RoomTileSettings(tile, room.tileName(state.entityName(tile.entity)), control.preference, control.canDim,
                onInput, ::edit)
        } else {
            EmptyState("This tile is unavailable", null)
        }
        return
    }
    val (visible, hidden) = remember(controls) {
        controls.filter { it.tile != null }.partition { !it.preference.hidden }
    }
    val custom = room.id in draft.roomTiles
    var search by rememberSaveable(room.id) { mutableStateOf("") }
    val matching = remember(hidden, search, state.catalogue) {
        val term = search.trim()
        hidden.filter { control ->
            val tile = checkNotNull(control.tile)
            state.entityName(tile.entity).contains(term, ignoreCase = true) ||
                tile.label?.contains(term, ignoreCase = true) == true ||
                tile.kind.label.contains(term, ignoreCase = true) || tile.entity.contains(term, ignoreCase = true)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = setupPadding),
        state = scroll,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoomEditorHeading("Shown in room")
                if (custom) TextButton(onClick = reset) { Text("Use automatic layout") }
            }
        }
        if (visible.isEmpty()) item {
            Text("No controls shown", Modifier.padding(start = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(visible, key = { _, control -> control.preference.entity }) { index, control ->
            val tile = checkNotNull(control.tile)
            val settings = control.preference
            val label = tile.label ?: room.tileName(state.entityName(tile.entity))
            val format = if (tile.kind == TileKind.SLIDER) "Dimmer" else tile.kind.label
            val size = if (settings.wideOnTablet) "Wide" else "Standard"
            RoomControlRow(label, "$format · $size", edit = { openTile(tile) }) {
                if (visible.size > 1) ReorderGrip(label, before = index > 0, after = index < visible.lastIndex) { delta ->
                    val other = visible.getOrNull(index + delta)?.preference ?: return@ReorderGrip
                    val next = preferences.toMutableList()
                    Collections.swap(next, next.indexOf(settings), next.indexOf(other))
                    update(next)
                }
            }
        }
        if (hidden.isNotEmpty()) item { RoomEditorHeading("Hidden controls", Modifier.padding(top = 6.dp)) }
        if (hidden.size >= 6 || search.isNotBlank()) item {
            SetupSearchField(search, { onInput(); search = it }, "Find a hidden control", Modifier.fillMaxWidth())
        }
        if (hidden.isNotEmpty() && matching.isEmpty()) item {
            Text("No matching controls", Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(matching, key = { it.preference.entity }) { control ->
            val tile = checkNotNull(control.tile)
            val label = tile.label ?: room.tileName(state.entityName(tile.entity))
            RoomControlRow(label, tile.kind.label) {
                IconButton(onClick = { edit(control.preference.copy(hidden = false)) }) {
                    Icon(Icons.Rounded.Visibility, "Show $label")
                }
            }
        }
    }
}

@Composable
private fun RoomEditorHeading(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun RoomControlRow(
    label: String, summary: String? = null, edit: (() -> Unit)? = null, actions: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val open = if (edit == null) Modifier else Modifier.clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = "Edit $label", onClick = edit)
            ListItem(
                modifier = Modifier.weight(1f).then(open).heightIn(min = 52.dp),
                headlineContent = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = summary?.let { { Text(it) } },
                trailingContent = edit?.let { { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            actions()
        }
    }
}

@Composable
private fun RoomTileSettings(
    tile: TileSpec, defaultName: String, settings: RoomTilePreference, canDim: Boolean,
    onInput: () -> Unit, edit: (RoomTilePreference) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = setupPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val format = if (tile.kind == TileKind.SLIDER) "Dimmer" else tile.kind.label
        TileEditorPreview(tile.label ?: defaultName, format, entityIcon(tile.entity.substringBefore('.')), settings.accent)
        TileNameField(settings.label.orEmpty(), defaultName) { name ->
            onInput()
            edit(settings.copy(label = name.takeIf(String::isNotEmpty)))
        }
        SettingsListGroup {
            SetupSwitchRow("Show in room", null, !settings.hidden) { edit(settings.copy(hidden = !it)) }
        }
        if (canDim) {
            SettingsListGroup("Light control") {
                SettingChoices(listOf(false, true), tile.kind == TileKind.SLIDER, { if (it) "Dimmer" else "Switch" }) { dimmer ->
                    edit(settings.copy(lightFormat = if (dimmer) TileKind.SLIDER else TileKind.TOGGLE))
                }
            }
        }
        TileSizePicker(settings.wideOnTablet) { wide ->
            edit(settings.copy(wideOnTablet = wide))
        }
        TileAccentPicker(settings.accent) { accent ->
            edit(settings.copy(accent = accent))
        }
    }
}
