package app.flickpanel.ha

import java.time.Instant
import java.time.format.DateTimeParseException

internal enum class TimerPhase { IDLE, ACTIVE, PAUSED }

internal data class TimerSnapshot(
    val phase: TimerPhase,
    val endsAtMillis: Long? = null,
    val pausedSeconds: Long? = null,
    val durationSeconds: Long? = null,
) {
    fun remainingFraction(seconds: Long?): Float? {
        val duration = durationSeconds?.takeIf { it > 0 } ?: return null
        return seconds?.let { (it.toFloat() / duration).coerceIn(0f, 1f) }
    }
}

internal fun Entity.timerSnapshot(): TimerSnapshot? {
    if (domain != "timer" || !available) return null
    if (state == "idle") return TimerSnapshot(TimerPhase.IDLE)
    val duration = timerDuration(attributes.text("duration"))
    return when (state) {
        "active" -> TimerSnapshot(TimerPhase.ACTIVE, endsAtMillis = timerDeadline(attributes.text("finishes_at")),
            durationSeconds = duration)
        "paused" -> TimerSnapshot(TimerPhase.PAUSED, pausedSeconds = timerDuration(attributes.text("remaining")),
            durationSeconds = duration)
        else -> null
    }
}

internal fun timerSecondsRemaining(deadline: Long, now: Long): Long {
    if (deadline <= now) return 0
    val millis = (deadline - now).takeIf { it > 0 } ?: Long.MAX_VALUE
    return (millis - 1) / 1000 + 1
}

private fun timerDeadline(value: String?): Long? {
    if (value == null) return null
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

private fun timerDuration(value: String?): Long? {
    val parts = value?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
    if (parts.size != 3 || parts[0] !in 0..100_000 || parts[1] !in 0..59 || parts[2] !in 0..59) return null
    return parts[0] * 3600 + parts[1] * 60 + parts[2]
}
