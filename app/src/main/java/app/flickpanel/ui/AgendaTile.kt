package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.AgendaContent
import app.flickpanel.AgendaState
import app.flickpanel.config.TileKind
import app.flickpanel.ha.AgendaEvent
import app.flickpanel.ha.AgendaTask

@Composable
internal fun AgendaPreview(
    title: String, kind: TileKind, state: AgendaState, online: Boolean, modifier: Modifier,
    expanded: Boolean, wide: Boolean, open: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dates = rememberAgendaDates()
    val content = state.content
    val tasks = remember(content) { (content as? AgendaContent.Tasks)?.items?.filterNot(AgendaTask::completed).orEmpty() }
    Surface(onClick = open, modifier = modifier.semantics { onClick(label = "Open $title", action = null) },
        color = colors.surfaceContainer, shape = panelCardShape) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = if (kind == TileKind.TASKS) Icons.Rounded.Checklist else Icons.Rounded.CalendarMonth
                Icon(icon, null, Modifier.size(compactTileIconSize), tint = colors.primary)
                TileTitle(title, Modifier.weight(1f))
                if (expanded && tasks.isNotEmpty()) TileStatus("${tasks.size} left", maxLines = 1, color = colors.primary)
            }
            when (content) {
                is AgendaContent.Tasks -> TasksPreview(tasks, dates, expanded, wide)
                is AgendaContent.Calendar -> CalendarPreview(content.events, dates, expanded, wide)
                null -> TileStatus(when {
                    !online -> "Unavailable"
                    state.issue != null -> "Couldn't load"
                    else -> "Loading…"
                })
            }
            if (state.content != null && (!online || state.issue != null)) TileStatus("Last update")
        }
    }
}

@Composable
private fun TasksPreview(open: List<AgendaTask>, dates: AgendaDates, expanded: Boolean, wide: Boolean) {
    val first = open.firstOrNull()
    if (first == null) {
        TileValue("All done")
        return
    }
    if (expanded) {
        val accent = MaterialTheme.colorScheme.primary
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (task in open.take(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).background(accent, CircleShape))
                    Column {
                        TileTitle(task.summary, maxLines = 1)
                        task.due?.let { TileStatus(dates.dueText(it), maxLines = 1) }
                    }
                }
            }
        }
        return
    }
    val detail = listOfNotNull(first.due?.let(dates::dueText), "${open.size} to do").joinToString(" · ")
    AgendaPreviewItem(first.summary, detail)
    if (wide) open.getOrNull(1)?.let { next ->
        AgendaPreviewItem(next.summary, next.due?.let(dates::dueText), prominent = false)
    }
}

@Composable
private fun CalendarPreview(events: List<AgendaEvent>, dates: AgendaDates, expanded: Boolean, wide: Boolean) {
    if (events.isEmpty()) {
        if (expanded) {
            AgendaPreviewItem(dates.todayLabel(), "No upcoming events")
        } else {
            TileValue("No upcoming events", maxLines = 2)
        }
    } else {
        for ((index, event) in events.take(if (expanded || wide) 2 else 1).withIndex()) {
            AgendaPreviewItem(event.summary, dates.eventSubtitle(event), prominent = index == 0)
        }
    }
}

@Composable
private fun AgendaPreviewItem(title: String, detail: String?, prominent: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (prominent) TileValue(title, maxLines = 2) else TileTitle(title, maxLines = 1)
        if (detail != null) TileStatus(detail, color = if (prominent) colors.primary else colors.onSurfaceVariant)
    }
}
