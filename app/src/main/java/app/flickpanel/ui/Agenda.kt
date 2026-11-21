package app.flickpanel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.AgendaState
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import app.flickpanel.ha.canAddTasks
import app.flickpanel.ha.canUpdateTasks
import kotlinx.coroutines.delay

@Composable
internal fun AgendaTile(tile: TileSpec, panel: PanelUi, model: PanelViewModel, modifier: Modifier) {
    val online = panel.link == Link.ONLINE && panel.entities[tile.entity]?.available == true
    val state = agendaState(tile.entity, online, model)
    AgendaPreview(panel.label(tile), tile.kind, state, online, modifier,
        limit = if (tile.wideOnTablet) 2 else 1) { model.openAgenda(tile.entity) }
}

@Composable
internal fun OpenAgenda(tile: TileSpec, panel: PanelUi, model: PanelViewModel) {
    val live = panel.entities[tile.entity]
    val online = panel.link == Link.ONLINE && live?.available == true
    val state = agendaState(tile.entity, online, model)
    AgendaScreen(panel.label(tile), tile.kind, state, online, live?.canAddTasks == true, live?.canUpdateTasks == true,
        close = model::closeAgenda, refresh = { model.loadAgenda(tile.entity, force = true) },
        add = { model.addTask(tile.entity, it) }, complete = { uid, checked -> model.completeTask(tile.entity, uid, checked) })
}

@Composable
private fun agendaState(id: String, online: Boolean, model: PanelViewModel): AgendaState {
    val states by model.agenda.states.collectAsStateWithLifecycle()
    LaunchedEffect(id, online) {
        if (!online) return@LaunchedEffect
        while (true) {
            model.loadAgenda(id)
            delay(60_000)
        }
    }
    return states[id] ?: AgendaState()
}
