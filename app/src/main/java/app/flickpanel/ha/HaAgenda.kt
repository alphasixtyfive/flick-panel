package app.flickpanel.ha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

private const val CREATE_TODO_ITEM = 1L
private const val UPDATE_TODO_ITEM = 4L
internal const val MAX_TASK_TITLE_LENGTH = 255

internal val Entity.canAddTasks: Boolean
    get() = domain == "todo" && attributes.optLong("supported_features") and CREATE_TODO_ITEM != 0L

internal val Entity.canUpdateTasks: Boolean
    get() = domain == "todo" && attributes.optLong("supported_features") and UPDATE_TODO_ITEM != 0L

internal sealed interface AgendaMoment {
    data class AllDay(val date: LocalDate) : AgendaMoment
    data class Timed(val instant: Instant) : AgendaMoment

    fun atStart(zone: ZoneId): Instant = when (this) {
        is AllDay -> date.atStartOfDay(zone).toInstant()
        is Timed -> instant
    }
}

internal data class AgendaTask(
    val uid: String,
    val summary: String,
    val completed: Boolean,
    val due: AgendaMoment? = null,
)

internal data class AgendaEvent(val summary: String, val start: AgendaMoment, val end: AgendaMoment)

internal fun validTaskUid(value: String): Boolean =
    value.isNotBlank() && value.length <= 1_024 && value.none(Char::isISOControl)

internal fun normalizedTaskTitle(value: String): String? = value.trim().takeIf {
    it.isNotEmpty() && it.length <= MAX_TASK_TITLE_LENGTH && it.none(Char::isISOControl)
}

internal fun decodeAgendaTasks(rows: JSONArray): List<AgendaTask> =
    rows.entries().mapNotNull { row ->
        val uid = (row.opt("uid") as? String)?.takeIf(::validTaskUid) ?: return@mapNotNull null
        val summary = row.summary() ?: return@mapNotNull null
        val completed = when (row.opt("status")) {
            "completed" -> true
            "needs_action" -> false
            else -> return@mapNotNull null
        }
        AgendaTask(uid, summary, completed, moment(row.opt("due") as? String))
    }.distinctBy(AgendaTask::uid).sortedBy(AgendaTask::completed).take(256).toList()

internal fun decodeAgendaEvents(rows: JSONArray, zone: ZoneId): List<AgendaEvent> =
    rows.entries().mapNotNull { row ->
        val start = moment(row.opt("start") as? String) ?: return@mapNotNull null
        val parsedEnd = moment(row.opt("end") as? String) ?: return@mapNotNull null
        val end = when {
            start is AgendaMoment.AllDay && parsedEnd is AgendaMoment.AllDay -> {
                if (parsedEnd.date < start.date) return@mapNotNull null
                if (parsedEnd.date == start.date) AgendaMoment.AllDay(parsedEnd.date.plusDays(1)) else parsedEnd
            }
            start is AgendaMoment.Timed && parsedEnd is AgendaMoment.Timed ->
                parsedEnd.takeIf { it.instant >= start.instant } ?: return@mapNotNull null
            else -> return@mapNotNull null
        }
        AgendaEvent(row.summary() ?: "Untitled event", start, end)
    }.sortedWith(compareBy({ it.start.atStart(zone) }, { it.start is AgendaMoment.Timed }))
        .take(128).toList()

private fun JSONArray.entries(): Sequence<JSONObject> =
    (0 until minOf(length(), 4_096)).asSequence().mapNotNull(::optJSONObject)

private fun JSONObject.summary(): String? = (opt("summary") as? String)
    ?.trim()?.take(500)?.takeIf(String::isNotEmpty)

private fun moment(value: String?): AgendaMoment? {
    val text = value?.takeIf { it.length in 10..35 } ?: return null
    return runCatching {
        if (text.length == 10) AgendaMoment.AllDay(LocalDate.parse(text))
        else AgendaMoment.Timed(OffsetDateTime.parse(text).toInstant())
    }.getOrNull()
}
