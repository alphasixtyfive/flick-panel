package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.AgendaContent
import app.flickpanel.AgendaState
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.AgendaTask
import app.flickpanel.ha.Link
import app.flickpanel.ha.canAddTasks
import app.flickpanel.ha.canUpdateTasks
import kotlinx.coroutines.delay

@Composable
internal fun AgendaTile(tile: TileSpec, panel: PanelUi, model: PanelViewModel, modifier: Modifier) {
    val online = panel.link == Link.ONLINE && panel.entities[tile.entity]?.available == true
    val states by model.agenda.states.collectAsStateWithLifecycle()
    RefreshAgenda(tile.entity, online, model)
    AgendaPreview(panel.label(tile), tile.kind, states[tile.entity] ?: AgendaState(), online, modifier,
        limit = if (tile.wideOnTablet) 3 else 2) { model.openAgenda(tile.entity) }
}

@Composable
internal fun AgendaPreview(
    title: String, kind: TileKind, state: AgendaState, online: Boolean, modifier: Modifier = Modifier,
    limit: Int = 2, open: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dates = rememberAgendaDates()
    Surface(onClick = open, modifier = modifier, color = colors.surfaceContainer, shape = panelCardShape) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (kind == TileKind.TASKS) Icons.Rounded.Checklist else Icons.Rounded.CalendarMonth,
                    null, Modifier.size(22.dp), tint = colors.primary)
                TileTitle(title, Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
            }
            val message = agendaMessage(kind, state, online)
            if (message != null) AgendaSubtitle(message)
            when (val content = state.content) {
                is AgendaContent.Tasks -> for (item in content.items.asSequence().filterNot { it.completed }.take(limit)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
                        Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                is AgendaContent.Calendar -> for (event in content.events.take(limit)) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(event.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AgendaSubtitle(dates.eventSubtitle(event))
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
internal fun OpenAgenda(tile: TileSpec, panel: PanelUi, model: PanelViewModel) {
    val states by model.agenda.states.collectAsStateWithLifecycle()
    val state = states[tile.entity] ?: AgendaState()
    val live = panel.entities[tile.entity]
    val online = panel.link == Link.ONLINE && live?.available == true
    RefreshAgenda(tile.entity, online, model)
    AgendaDialog(panel.label(tile), tile.kind, state, online, live?.canAddTasks == true, live?.canUpdateTasks == true,
        close = model::closeAgenda, refresh = { model.loadAgenda(tile.entity, force = true) },
        add = { model.addTask(tile.entity, it) }, complete = { uid, checked -> model.completeTask(tile.entity, uid, checked) })
}

@Composable
private fun RefreshAgenda(id: String, online: Boolean, model: PanelViewModel) {
    LaunchedEffect(id, online) {
        if (!online) return@LaunchedEffect
        while (true) {
            model.loadAgenda(id)
            delay(60_000)
        }
    }
}

@Composable
internal fun AgendaDialog(
    title: String, kind: TileKind, state: AgendaState, online: Boolean, canAdd: Boolean, canComplete: Boolean,
    close: () -> Unit, refresh: () -> Unit, add: (String) -> Boolean, complete: (String, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dates = rememberAgendaDates()
    val ready = online && !state.loading && !state.saving
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight(.82f).imePadding(),
            shape = MaterialTheme.shapes.extraLarge, color = colors.surface) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(start = 22.dp, end = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = refresh, enabled = ready) { Icon(Icons.Rounded.Refresh, "Refresh list") }
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Close list") }
                }
                if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (kind == TileKind.TASKS && canAdd) {
                        item(key = "new-task") { AddTaskField(state, ready, add) }
                    }
                    agendaMessage(kind, state, online)?.let { message ->
                        item(key = "agenda-status") {
                            Text(message, Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    when (val content = state.content) {
                        is AgendaContent.Tasks -> taskRows(content.items, dates, canComplete, ready, complete)
                        is AgendaContent.Calendar -> calendarRows(content.events, dates)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgendaSection(title: String) {
    Text(title, Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun AgendaSubtitle(value: String) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun agendaMessage(kind: TileKind, state: AgendaState, online: Boolean) = when {
    !online -> if (state.content == null) "List unavailable" else "Showing last update"
    state.issue != null -> state.issue
    state.content == null -> "Loading…"
    kind == TileKind.TASKS && (state.content as? AgendaContent.Tasks)?.items.orEmpty().all(AgendaTask::completed) -> "No open tasks"
    kind == TileKind.CALENDAR && (state.content as? AgendaContent.Calendar)?.events.orEmpty().isEmpty() -> "No events in the next 7 days"
    else -> null
}
