package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Area
import app.flickpanel.ha.Link

@Composable
internal fun Rooms(
    state: PanelUi, model: PanelViewModel, roomId: String?,
    selectRoom: (String) -> Unit, modifier: Modifier,
) {
    val sections = state.roomSections
    val selected = remember(sections, roomId) { sections.firstOrNull { it.area.id == roomId } ?: sections.firstOrNull() }
    val roomScroll = rememberSaveableStateHolder()
    if (selected == null) {
        val loading = state.loadingCatalogue || state.link == Link.CONNECTING
        val hasRooms = state.catalogue.rooms.isNotEmpty()
        val needsSelection = hasRooms && state.layout.selectedRooms.isEmpty()
        val canConfigure = !loading && state.link == Link.ONLINE && hasRooms
        val title = when {
            loading -> "Finding rooms…"
            needsSelection -> "No rooms selected"
            else -> "No rooms to show"
        }
        val note = when {
            loading || state.link != Link.ONLINE -> null
            canConfigure -> "Choose Rooms in setup to manage what appears here."
            else -> "Assign lights to an area in Home Assistant."
        }
        Column(modifier) {
            EmptyState(title, note) {
                if (canConfigure) TextButton(onClick = model::showUnlock) { Text("Open setup") }
            }
        }
        return
    }

    val rooms = remember(sections) { sections.map { it.area } }
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val listWidth = (188.dp * fontScale).coerceIn(188.dp, 256.dp)
        val paneGap = 24.dp
        val detailColumns = roomTileColumns((maxWidth - listWidth - paneGap).value, fontScale)
        val split = rooms.size > 1 && maxWidth >= tabletWidthDp.dp - panelWidePadding * 2 && detailColumns >= 2
        val gridColumns = if (split) detailColumns else roomTileColumns(maxWidth.value, fontScale)
        val compact = gridColumns >= 4
        val rows = remember(selected.tiles, gridColumns) {
            packTileRows(selected.tiles, gridColumns, compact, resizeTiles = true)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(paneGap)) {
            if (split) {
                RoomList(rooms, selected.area.id, selectRoom, Modifier.width(listWidth).fillMaxHeight())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                if (!split && rooms.size > 1) {
                    RoomPicker(rooms, selected.area, selectRoom)
                } else {
                    Text(
                        selected.area.name,
                        modifier = Modifier.padding(vertical = 9.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                roomScroll.SaveableStateProvider(selected.area.id) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(tileGap),
                    ) {
                        if (rows.isEmpty()) {
                            item {
                                EmptyState("No controls shown", "Choose Rooms in setup to change this layout.") {
                                    TextButton(onClick = model::showUnlock) { Text("Open setup") }
                                }
                            }
                        } else {
                            tileRows(rows, state, model, gridColumns, compact, resizeTiles = true, keyPrefix = "room-tile:")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomList(rooms: List<Area>, selected: String, selectRoom: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberLazyListState()
    LazyColumn(
        modifier.selectableGroup(),
        state = scroll,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rooms, key = Area::id) { room ->
            val active = room.id == selected
            Surface(
                selected = active,
                onClick = { selectRoom(room.id) },
                shape = MaterialTheme.shapes.large,
                color = if (active) colors.secondaryContainer else Color.Transparent,
                contentColor = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.SemiBold else null, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPicker(rooms: List<Area>, room: Area, selectRoom: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { expanded = true },
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
    ) {
        Text(room.name, Modifier.weight(1f, fill = false).padding(end = 8.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Choose room", modifier = Modifier.size(24.dp))
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text("Choose room", Modifier.padding(start = 32.dp, bottom = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall)
            RoomList(rooms, room.id, {
                selectRoom(it)
                expanded = false
            }, Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(horizontal = 16.dp))
        }
    }
}
