package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.AgendaContent
import app.flickpanel.AgendaState
import app.flickpanel.config.TileKind
import app.flickpanel.ha.AgendaTask
import kotlinx.coroutines.launch

private val agendaMaxWidth = 760.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaScreen(
    title: String, kind: TileKind, state: AgendaState, online: Boolean, canAdd: Boolean, canComplete: Boolean,
    close: () -> Unit, refresh: () -> Unit, add: (String) -> Boolean, complete: (String, Boolean) -> Unit,
) {
    val dates = rememberAgendaDates()
    val ready = online && !state.loading && !state.saving
    var adding by rememberSaveable { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val allowAdd = kind == TileKind.TASKS && canAdd

    Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            TopAppBar(
                modifier = Modifier.widthIn(max = agendaMaxWidth).fillMaxWidth(),
                title = { Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to dashboard") } },
                actions = {
                    IconButton(onClick = refresh, enabled = ready) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    if (allowAdd) {
                        IconButton(onClick = {
                            adding = true
                            scope.launch { list.scrollToItem(0) }
                        }, enabled = ready && !adding) { Icon(Icons.Rounded.Add, "Add task") }
                    }
                },
            )
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = agendaMaxWidth).fillMaxSize()) {
                if (state.saving || state.loading && state.content == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (adding && allowAdd) item(key = "new-task") {
                        AddTaskField(state, ready, add, close = { adding = false })
                    }
                    screenMessage(state, online)?.let { message ->
                        item(key = "agenda-status") { TileStatus(message, Modifier.padding(vertical = 6.dp)) }
                    }
                    when (val content = state.content) {
                        is AgendaContent.Tasks -> {
                            if (content.items.all(AgendaTask::completed)) item(key = "empty-tasks") {
                                AgendaSection("All done")
                            }
                            taskRows(content.items, dates, canComplete, ready, showCompleted,
                                toggleCompleted = { showCompleted = !showCompleted }, complete = complete)
                        }
                        is AgendaContent.Calendar -> {
                            if (content.events.isEmpty()) item(key = "empty-calendar") {
                                AgendaSection("No events in the next 7 days")
                            }
                            calendarRows(content.events, dates)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgendaSection(title: String) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun AgendaSubtitle(value: String) {
    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun screenMessage(state: AgendaState, online: Boolean) = when {
    !online -> if (state.content == null) "Unavailable" else "Showing last update"
    state.issue != null -> state.issue
    state.content == null -> "Loading…"
    else -> null
}
