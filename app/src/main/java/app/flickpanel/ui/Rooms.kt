package app.flickpanel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Area
import app.flickpanel.ha.Link

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoomTitle(model: PanelViewModel, wide: Boolean, modifier: Modifier) {
    Text(
        "Rooms",
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClickLabel = "Unlock setup",
            onLongClick = model::showUnlock,
        ).heightIn(min = 48.dp).semantics { heading() },
        style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Rooms(state: PanelUi, model: PanelViewModel, wide: Boolean, modifier: Modifier) {
    val rooms = state.rooms
    val selected = rooms.firstOrNull { it.id == state.room } ?: state.preferredRoom
    if (selected == null) {
        val loading = state.loadingCatalogue || state.link == Link.CONNECTING
        val needsSelection = state.config.roomIds?.isEmpty() == true || !loading && state.catalogue.rooms.isNotEmpty()
        val title = when {
            needsSelection -> "No rooms selected"
            loading -> "Finding rooms…"
            else -> "No rooms to show"
        }
        val note = when {
            needsSelection -> "Hold Rooms to unlock setup and choose which rooms to show."
            loading -> null
            state.link == Link.ONLINE -> "Assign lights or thermostats to an area in Home Assistant."
            else -> null
        }
        Column(modifier) {
            EmptyState(title, note)
        }
        return
    }

    LaunchedEffect(selected.id, state.room) {
        if (state.room != selected.id) model.selectRoom(selected.id)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoomChoices(rooms, selected.id, selectRoom = model::selectRoom)
        key(selected.id) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                TileGrid(state.roomTiles, state, model, if (wide) 4 else 2)
            }
        }
    }
}

@Composable
private fun RoomChoices(
    rooms: List<Area>,
    selected: String,
    selectRoom: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectedIndex = rooms.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(selected, selectedIndex) {
        if (scroll.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            scroll.scrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = scroll,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rooms, key = { it.id }) { room ->
            val active = room.id == selected
            Surface(
                selected = active,
                onClick = { selectRoom(room.id) },
                modifier = Modifier.semantics { role = Role.Tab },
                shape = RoundedCornerShape(14.dp),
                color = if (active) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (active) colors.onPrimaryContainer else colors.onSurface,
            ) {
                Box(
                    Modifier.widthIn(min = 104.dp, max = 184.dp).heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        room.name,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
