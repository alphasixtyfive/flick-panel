package app.flickpanel.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
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
import app.flickpanel.ha.AgendaEvent
import app.flickpanel.ha.AgendaMoment
import app.flickpanel.ha.AgendaTask
import app.flickpanel.ha.Link
import app.flickpanel.ha.MAX_TASK_TITLE_LENGTH
import app.flickpanel.ha.canAddTasks
import app.flickpanel.ha.canUpdateTasks
import app.flickpanel.ha.normalizedTaskTitle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (kind == TileKind.TASKS) Icons.Rounded.Checklist else Icons.Rounded.CalendarMonth,
                    null, Modifier.size(22.dp), tint = colors.primary)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun AddTaskField(state: AgendaState, ready: Boolean, add: (String) -> Boolean) {
    var draft by rememberSaveable { mutableStateOf("") }
    var submittedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val valid = normalizedTaskTitle(draft)
    val submit = {
        if (ready && valid != null && add(valid)) submittedAt = state.savedRevision
    }
    LaunchedEffect(state.saving, state.savedRevision) {
        val revision = submittedAt
        if (!state.saving && revision != null) {
            if (revision != state.savedRevision) draft = ""
            submittedAt = null
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft, { draft = it.take(MAX_TASK_TITLE_LENGTH) }, Modifier.weight(1f), singleLine = true,
            enabled = !state.saving, label = { Text("New task") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        Button(onClick = submit, enabled = ready && valid != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add") }
    }
}

private fun LazyListScope.taskRows(
    tasks: List<AgendaTask>, dates: AgendaDates, canComplete: Boolean, ready: Boolean, complete: (String, Boolean) -> Unit,
) {
    items(tasks.filterNot(AgendaTask::completed), key = { "open:${it.uid}" }) { task ->
        TaskRow(task, dates, canComplete, ready) { complete(task.uid, it) }
    }
    val completed = tasks.filter(AgendaTask::completed)
    if (completed.isNotEmpty()) {
        item(key = "completed-header") { AgendaSection("Completed") }
        items(completed, key = { "completed:${it.uid}" }) { task ->
            TaskRow(task, dates, canComplete, ready) { complete(task.uid, it) }
        }
    }
}

@Composable
private fun TaskRow(task: AgendaTask, dates: AgendaDates, writable: Boolean, ready: Boolean, change: (Boolean) -> Unit) {
    val interaction = if (writable) Modifier.toggleable(task.completed, enabled = ready, role = Role.Checkbox, onValueChange = change)
    else Modifier.semantics(mergeDescendants = true) { stateDescription = if (task.completed) "Completed" else "Open" }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(interaction).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = task.completed, onCheckedChange = null, enabled = !writable || ready)
            Column(Modifier.weight(1f)) {
                Text(task.summary, style = MaterialTheme.typography.titleMedium)
                task.due?.let { date -> AgendaSubtitle(dates.dueText(date)) }
            }
        }
    }
}

private fun LazyListScope.calendarRows(events: List<AgendaEvent>, dates: AgendaDates) {
    for ((day, grouped) in events.groupBy(dates::eventDay)) {
        item { AgendaSection(dates.dayName(day)) }
        items(grouped) { event ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(event.summary, style = MaterialTheme.typography.titleMedium)
                    AgendaSubtitle(dates.eventTime(event))
                }
            }
        }
    }
}

@Composable
private fun AgendaSection(title: String) {
    Text(title, Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AgendaSubtitle(value: String) {
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

@Composable
private fun rememberAgendaDates(): AgendaDates {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(zone, today, use24Hour) { AgendaDates(zone, today, use24Hour) }
}

private class AgendaDates(private val zone: ZoneId, private val today: LocalDate, use24Hour: Boolean) {
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
    private val clock = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")

    fun day(value: AgendaMoment): LocalDate = when (value) {
        is AgendaMoment.AllDay -> value.date
        is AgendaMoment.Timed -> value.instant.atZone(zone).toLocalDate()
    }

    fun eventDay(event: AgendaEvent) = maxOf(today, day(event.start))

    fun dayName(date: LocalDate) = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(shortDate)
    }

    fun startTime(value: AgendaMoment): String = when (value) {
        is AgendaMoment.AllDay -> "All day"
        is AgendaMoment.Timed -> value.instant.atZone(zone).format(clock)
    }

    fun dueText(value: AgendaMoment): String {
        val date = day(value)
        val name = if (date == today || date == today.plusDays(1)) dayName(date).lowercase() else dayName(date)
        val time = if (value is AgendaMoment.Timed) " · ${startTime(value)}" else ""
        return "Due $name$time"
    }

    fun eventSubtitle(event: AgendaEvent): String {
        val caption = if (event.start is AgendaMoment.Timed && day(event.start) < today) "In progress"
        else startTime(event.start)
        return "${dayName(eventDay(event))} · $caption"
    }

    fun eventTime(event: AgendaEvent): String {
        val startDay = day(event.start)
        val endDay = day(event.end)
        if (event.start is AgendaMoment.AllDay) {
            val lastDay = endDay.minusDays(1)
            return if (startDay == lastDay) "All day"
            else "All day · ${startDay.format(shortDate)} – ${lastDay.format(shortDate)}"
        }
        if (event.start == event.end) return startTime(event.start)
        val start = if (startDay < today) "${startDay.format(shortDate)} ${startTime(event.start)}" else startTime(event.start)
        val end = if (startDay == endDay) startTime(event.end)
        else "${endDay.format(shortDate)} ${startTime(event.end)}"
        return "$start – $end"
    }
}
