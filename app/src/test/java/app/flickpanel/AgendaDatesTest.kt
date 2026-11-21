package app.flickpanel

import app.flickpanel.ha.AgendaEvent
import app.flickpanel.ha.AgendaMoment
import app.flickpanel.ui.AgendaDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AgendaDatesTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val dates = AgendaDates(ZoneId.of("Europe/London"), today, use24Hour = true)

    @Test fun dueLabelsUseTheLocalDayAndDistinguishOverdueTasks() {
        assertEquals("Due today", dates.dueText(AgendaMoment.AllDay(today)))
        val dueTomorrow = AgendaMoment.Timed(Instant.parse("2026-11-22T01:05:00Z"))
        assertEquals("Due tomorrow · 01:05", dates.dueText(dueTomorrow))
        assertTrue(dates.dueText(AgendaMoment.AllDay(today.minusDays(1))).startsWith("Overdue · "))
    }

    @Test fun anOngoingEventStaysInTodaysAgenda() {
        val event = AgendaEvent("Overnight trip", AgendaMoment.Timed(Instant.parse("2026-11-20T23:00:00Z")),
            AgendaMoment.Timed(Instant.parse("2026-11-21T09:00:00Z")))
        assertEquals(today, dates.eventDay(event))
        assertEquals("Today · In progress", dates.eventSubtitle(event))
        assertTrue(dates.dayHeading(today).startsWith("Today · "))
    }
}
