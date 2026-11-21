package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.RoomLayout
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Area
import java.util.Collections

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomControlsEditor(
    room: Area, draft: PanelConfig, state: PanelUi,
    update: (List<RoomTilePreference>) -> Unit, reset: () -> Unit,
) {
    val controls = remember(room, draft, state.catalogue) { RoomLayout(draft, state.catalogue).controls(room) }
    val preferences = remember(controls) { controls.map { it.preference } }
    val (visible, hidden) = remember(controls) {
        controls.filter { it.tile != null }.partition { !it.preference.hidden }
    }
    val custom = room.id in draft.roomTiles
    fun edit(preference: RoomTilePreference) {
        update(preferences.map { if (it.entity == preference.entity) preference else it })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (custom) "Custom layout" else "Automatic layout",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (custom) TextButton(onClick = reset) { Text("Use automatic layout") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (visible.isEmpty()) item { Text("No controls shown", Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(visible, key = { _, control -> control.preference.entity }) { index, control ->
                val tile = checkNotNull(control.tile)
                val settings = control.preference
                val label = state.label(tile)
                RoomControlRow(label, tile.kind, settings) {
                    ReorderGrip(label, before = index > 0, after = index < visible.lastIndex) { delta ->
                        val other = visible.getOrNull(index + delta)?.preference ?: return@ReorderGrip
                        val next = preferences.toMutableList()
                        Collections.swap(next, next.indexOf(settings), next.indexOf(other))
                        update(next)
                    }
                    RoomControlOptions(label, tile.kind, settings, control.canDim, ::edit)
                }
            }
            if (hidden.isNotEmpty()) item {
                Text("Hidden controls", Modifier.padding(top = 14.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            }
            items(hidden, key = { it.preference.entity }) { control ->
                val tile = checkNotNull(control.tile)
                val label = state.label(tile)
                RoomControlRow(label, tile.kind, control.preference) {
                    IconButton(onClick = { edit(control.preference.copy(hidden = false)) }) {
                        Icon(Icons.Rounded.Visibility, "Show $label")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomControlRow(
    label: String, kind: TileKind, settings: RoomTilePreference, actions: @Composable () -> Unit,
) {
    val format = if (kind == TileKind.SLIDER) "Dimmer" else kind.label
    val size = if (settings.wideOnTablet) "Wide" else "Standard"
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text("$format · $size") },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { actions() } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun RoomControlOptions(
    label: String, kind: TileKind, settings: RoomTilePreference, canDim: Boolean,
    edit: (RoomTilePreference) -> Unit,
) {
    var opened by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { opened = true }) { Icon(Icons.Rounded.MoreVert, "Options for $label") }
        DropdownMenu(expanded = opened, onDismissRequest = { opened = false }) {
            if (canDim) {
                val dimmer = kind == TileKind.SLIDER
                DropdownMenuItem(
                    text = { Text(if (dimmer) "Use switch" else "Use dimmer") },
                    onClick = {
                        opened = false
                        edit(settings.copy(lightFormat = if (dimmer) TileKind.TOGGLE else TileKind.SLIDER))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (settings.wideOnTablet) "Make standard" else "Make wide") },
                onClick = {
                    opened = false
                    edit(settings.copy(wideOnTablet = !settings.wideOnTablet))
                },
            )
            DropdownMenuItem(
                text = { Text("Hide tile") },
                onClick = {
                    opened = false
                    edit(settings.copy(hidden = true))
                },
            )
        }
    }
}
