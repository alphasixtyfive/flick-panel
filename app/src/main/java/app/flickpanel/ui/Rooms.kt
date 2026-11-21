package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Link

@Composable
internal fun Rooms(state: PanelUi, model: PanelViewModel, columns: Int, modifier: Modifier) {
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

    val rows = remember(sections, columns) {
        sections.map { section -> packTileRows(section.tiles, columns, compact = false, resizeTiles = false) }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(tileGap),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "room:${section.area.id}", contentType = "room-heading") {
                Text(
                    section.area.name,
                    modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 12.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            tileRows(rows[index], state, model, columns, compact = false, resizeTiles = false,
                keyPrefix = "room-tile:${section.area.id}:")
        }
    }
}
