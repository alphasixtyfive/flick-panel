package app.flickpanel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
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
    val sections = state.roomSections
    if (sections.isEmpty()) {
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

    val columns = if (wide) 4 else 2
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        items(sections, key = { it.area.id }) { section ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    section.area.name,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                TileGrid(section.tiles, state, model, columns)
            }
        }
    }
}
