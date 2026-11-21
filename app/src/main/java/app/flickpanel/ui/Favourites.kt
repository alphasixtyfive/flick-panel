package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec

private val overviewCameraHeight = 154.dp
private val overviewPersonHeight = (overviewCameraHeight + homeAgendaHeight - tileGap) / 3

@Composable
internal fun Favourites(
    groups: HomeTileGroups, state: PanelUi, model: PanelViewModel, modifier: Modifier, showReadings: Boolean,
) {
    val badges = state.activeHomeBadges()
    if (!groups.hasTiles && badges.isEmpty()) {
        Column(modifier) { EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.") }
        return
    }

    val columns = groups.columns
    val compact = groups.compact
    val resizable = groups.resizeTiles
    val firstCameraRow = groups.cameras.firstOrNull()
    val peopleBesideCameras = if (compact && firstCameraRow?.size == 2) groups.people.take(3) else emptyList()
    val agendaBesideCameras = groups.agenda.singleOrNull()?.takeIf {
        peopleBesideCameras.size == 3 && groups.agendaColumns == 2
    }
    val remainingPeople = remember(groups.people, peopleBesideCameras, columns, compact, resizable) {
        packTileRows(groups.people.drop(peopleBesideCameras.size).flatten(), columns, compact, resizable)
    }
    val hasReadings = showReadings && groups.readings.isNotEmpty()
    val scroll = key(hasReadings) { rememberLazyListState() }
    LazyColumn(
        modifier = modifier,
        state = scroll,
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(tileGap),
    ) {
        if (badges.isNotEmpty()) item(key = "home-warnings") { HomeWarnings(badges) }
        if (hasReadings) item(key = "home-readings") {
            HomeGlanceReadings(groups.readings, state)
        }
        tileRows(groups.controls, state, model, columns, compact, resizable)
        if (firstCameraRow != null && peopleBesideCameras.isNotEmpty()) {
            item(key = "home-glance", contentType = "home-glance") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                        val peopleHeight = personTileHeight * peopleBesideCameras.size + tileGap * (peopleBesideCameras.size - 1)
                        HomeCameraRow(
                            firstCameraRow, columns, state, model,
                            previewHeight = if (agendaBesideCameras == null) maxOf(186.dp, peopleHeight) else overviewCameraHeight,
                        )
                        if (agendaBesideCameras != null) {
                            TileRow(agendaBesideCameras, state, model, groups.agendaColumns, compact, resizable, homeAgenda = true)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                        for (row in peopleBesideCameras) {
                            TileRow(row, state, model, groups.peopleColumns, compact, resizable,
                                minPersonHeight = if (agendaBesideCameras != null) overviewPersonHeight else personTileHeight)
                        }
                    }
                }
            }
        }
        val cameraRows = if (peopleBesideCameras.isEmpty()) groups.cameras else groups.cameras.drop(1)
        items(cameraRows, key = { "home-cameras:" + it.first().entity }, contentType = { "home-cameras" }) { cameras ->
            HomeCameraRow(cameras, columns, state, model)
        }
        if (agendaBesideCameras == null) {
            val agendaColumns = if (compact) columns else groups.agendaColumns
            tileRows(groups.agenda, state, model, agendaColumns, compact, resizable, homeAgenda = true)
        }
        tileRows(remainingPeople, state, model, columns, compact, resizable)
        tileRows(groups.shortcuts.chunked(columns), state, model, columns, compact, resizable,
            shortcutRow = true)
        tileRows(groups.history, state, model, columns, compact, resizable)
    }
}

@Composable
private fun HomeCameraRow(
    cameras: List<TileSpec>,
    columns: Int,
    state: PanelUi,
    model: PanelViewModel,
    previewHeight: Dp = if (columns > 1) 142.dp else 180.dp,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
        for (camera in cameras) {
            CameraTile(camera, state, model, Modifier.weight(1f).height(previewHeight))
        }
        if (columns > 1 && cameras.size == 1) Spacer(Modifier.weight(1f))
    }
}
