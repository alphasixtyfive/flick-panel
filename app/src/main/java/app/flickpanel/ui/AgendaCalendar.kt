package app.flickpanel.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.AgendaEvent
import app.flickpanel.ha.AgendaMoment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun LazyListScope.calendarRows(events: List<AgendaEvent>, dates: AgendaDates) {
    for ((day, grouped) in events.groupBy(dates::eventDay)) {
        item(key = "day:$day") { AgendaSection(dates.dayHeading(day)) }
        items(grouped) { event ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp)) {
                    Text(event.summary, style = MaterialTheme.typography.titleLarge)
                    AgendaSubtitle(dates.eventTime(event))
                }
            }
        }
    }
}

@Composable
internal fun rememberAgendaDates(): AgendaDates {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(zone, today, use24Hour) { AgendaDates(zone, today, use24Hour) }
}

internal class AgendaDates(private val zone: ZoneId, private val today: LocalDate, use24Hour: Boolean) {
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
    private val clock = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")

    fun day(value: AgendaMoment): LocalDate = when (value) {
        is AgendaMoment.AllDay -> value.date
        is AgendaMoment.Timed -> value.instant.atZone(zone).toLocalDate()
    }

    fun eventDay(event: AgendaEvent) = maxOf(today, day(event.start))

    fun todayLabel() = today.format(shortDate)

    fun dayName(date: LocalDate) = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(shortDate)
    }

    fun dayHeading(date: LocalDate) = when (date) {
        today, today.plusDays(1) -> "${dayName(date)} · ${date.format(shortDate)}"
        else -> dayName(date)
    }

    fun startTime(value: AgendaMoment): String = when (value) {
        is AgendaMoment.AllDay -> "All day"
        is AgendaMoment.Timed -> value.instant.atZone(zone).format(clock)
    }

    fun dueText(value: AgendaMoment): String {
        val date = day(value)
        val name = if (date == today || date == today.plusDays(1)) dayName(date).lowercase() else dayName(date)
        val time = if (value is AgendaMoment.Timed) " · ${startTime(value)}" else ""
        val prefix = if (date < today) "Overdue ·" else "Due"
        return "$prefix $name$time"
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
