package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.AgendaContent
import app.flickpanel.AgendaState
import app.flickpanel.config.TileKind

private val agendaMaxWidth = 720.dp

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
    val allowAdd = kind == TileKind.TASKS && canAdd
    val tasks = state.content as? AgendaContent.Tasks

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxWidth >= 460.dp * LocalDensity.current.fontScale
        val gutter = if (roomy) 24.dp else 16.dp
        Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
            Box(Modifier.fillMaxWidth().padding(top = if (roomy) 12.dp else 4.dp), contentAlignment = Alignment.TopCenter) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = agendaMaxWidth).fillMaxWidth().padding(horizontal = gutter - 8.dp),
                    title = {
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(title, Modifier.semantics { heading() },
                                style = if (roomy) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tasks != null) Text(
                                "${tasks.open.size} left", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to dashboard") }
                    },
                    actions = {
                        IconButton(onClick = refresh, enabled = ready) { Icon(Icons.Rounded.Refresh, "Refresh") }
                        if (allowAdd) {
                            if (roomy) {
                                FilledTonalButton(onClick = { adding = true }, enabled = ready && !adding) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                                    Text("Add item", Modifier.padding(start = 8.dp))
                                }
                            } else {
                                FilledTonalIconButton(onClick = { adding = true }, enabled = ready && !adding) {
                                    Icon(Icons.Rounded.Add, "Add item")
                                }
                            }
                        }
                    },
                )
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = agendaMaxWidth).fillMaxSize(),
                    contentPadding = PaddingValues(start = gutter, end = gutter, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (kind == TileKind.TASKS) 0.dp else 8.dp),
                ) {
                    screenMessage(state, online)?.let { message ->
                        item(key = "agenda-status") { TileStatus(message, Modifier.padding(bottom = 12.dp)) }
                    }
                    when (val content = state.content) {
                        is AgendaContent.Tasks -> taskRows(content, dates, canComplete, ready, showCompleted,
                            toggleCompleted = { showCompleted = !showCompleted }, complete = complete)
                        is AgendaContent.Calendar -> {
                            if (content.events.isEmpty()) item(key = "empty-calendar") {
                                AgendaSection("No events in the next 7 days")
                            }
                            calendarRows(content.events, dates)
                        }
                        null -> Unit
                    }
                }
                if (state.saving || state.loading && state.content == null) {
                    LinearProgressIndicator(Modifier.widthIn(max = agendaMaxWidth).fillMaxWidth()
                        .padding(horizontal = gutter).align(Alignment.TopCenter))
                }
            }
        }
    }
    if (adding && allowAdd) AddTaskDialog(state, ready, add, close = { adding = false })
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
