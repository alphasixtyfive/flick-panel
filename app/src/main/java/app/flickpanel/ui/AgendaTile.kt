package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    title: String, kind: TileKind, state: AgendaState, online: Boolean, modifier: Modifier = Modifier,
    limit: Int = 1, open: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dates = rememberAgendaDates()
    Surface(onClick = open, modifier = modifier.semantics { onClick(label = "Open $title", action = null) },
        color = colors.surfaceContainer, shape = panelCardShape) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = if (kind == TileKind.TASKS) Icons.Rounded.Checklist else Icons.Rounded.CalendarMonth
                Icon(icon, null, Modifier.size(compactTileIconSize), tint = colors.primary)
                TileTitle(title, Modifier.weight(1f).align(Alignment.Top))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
            }
            when (val content = state.content) {
                is AgendaContent.Tasks -> TasksPreview(content.items, dates, limit)
                is AgendaContent.Calendar -> CalendarPreview(content.events, dates, limit)
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
private fun TasksPreview(tasks: List<AgendaTask>, dates: AgendaDates, limit: Int) {
    val open = tasks.filterNot(AgendaTask::completed)
    val first = open.firstOrNull()
    if (first == null) {
        TileValue("All done")
        return
    }
    val detail = listOfNotNull(first.due?.let(dates::dueText), "${open.size} to do").joinToString(" · ")
    AgendaPreviewItem(first.summary, detail)
    if (limit > 1) open.getOrNull(1)?.let { next ->
        AgendaPreviewItem(next.summary, next.due?.let(dates::dueText), prominent = false)
    }
}

@Composable
private fun CalendarPreview(events: List<AgendaEvent>, dates: AgendaDates, limit: Int) {
    if (events.isEmpty()) {
        TileValue("No upcoming events", maxLines = 2)
    } else {
        for ((index, event) in events.take(limit).withIndex()) {
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
