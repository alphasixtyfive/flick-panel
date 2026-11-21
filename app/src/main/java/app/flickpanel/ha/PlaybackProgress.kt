package app.flickpanel.ha

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

internal data class PlaybackProgress(val duration: Double, val position: Double, val advancingSince: Instant?) {
    fun positionAt(now: Instant): Double {
        val elapsed = advancingSince?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
        return (position + (elapsed ?: 0L)).coerceAtMost(duration)
    }
}

internal fun Entity.playbackProgress(): PlaybackProgress? {
    if (state != "playing" && state != "paused") return null
    val duration = attributes.optDouble("media_duration", Double.NaN).takeIf { it.isFinite() && it > 0 } ?: return null
    val position = attributes.optDouble("media_position", Double.NaN).takeIf { it.isFinite() && it >= 0 } ?: return null
    val stamp = attributes.text("media_position_updated_at")?.takeIf { state == "playing" }
    val updated = try {
        stamp?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
    return PlaybackProgress(duration, position.coerceAtMost(duration), updated)
}
