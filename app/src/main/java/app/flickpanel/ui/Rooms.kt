package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Area
import app.flickpanel.ha.Link

@Composable
internal fun Rooms(
    state: PanelUi, model: PanelViewModel, columns: Int, roomId: String?,
    selectRoom: (String) -> Unit, modifier: Modifier,
) {
    val sections = state.roomSections
    val selected = sections.firstOrNull { it.area.id == roomId } ?: sections.firstOrNull()
    if (selected == null) {
        val loading = state.loadingCatalogue || state.link == Link.CONNECTING
        val needsSelection = state.config.roomIds?.isEmpty() == true
        val title = when {
            needsSelection -> "No rooms selected"
            loading -> "Finding rooms…"
            else -> "No rooms to show"
        }
        val note = when {
            loading || state.link != Link.ONLINE -> null
            needsSelection || state.catalogue.rooms.isNotEmpty() -> "Hold Rooms to choose which rooms and controls to show."
            else -> "Assign lights to an area in Home Assistant."
        }
        Column(modifier) { EmptyState(title, note) }
        return
    }

    val rooms = remember(sections) { sections.map { it.area } }
    BoxWithConstraints(modifier) {
        val gridColumns = roomTileColumns(maxWidth.value, LocalDensity.current.fontScale, columns, selected.tiles.size)
        val compact = gridColumns >= 4
        val rows = remember(selected, gridColumns, compact) {
            packTileRows(selected.tiles, gridColumns, compact, resizeTiles = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
            if (rooms.size > 1) RoomSelector(rooms, selected.area.id, selectRoom)
            else Text(
                selected.area.name,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            key(selected.area.id) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(tileGap),
                ) {
                    tileRows(rows, state, model, gridColumns, compact, resizeTiles = true, keyPrefix = "room-tile:")
                }
            }
        }
    }
}

@Composable
internal fun RoomSelector(rooms: List<Area>, selected: String, selectRoom: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rooms, key = Area::id) { room ->
            val active = room.id == selected
            Surface(
                selected = active,
                onClick = { selectRoom(room.id) },
                shape = CircleShape,
                color = if (active) colors.secondaryContainer else colors.surfaceContainer,
                contentColor = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.semantics { role = Role.Tab },
            ) {
                Text(room.name, Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
