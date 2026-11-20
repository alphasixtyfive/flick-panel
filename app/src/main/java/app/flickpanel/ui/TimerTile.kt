package app.flickpanel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.text
import kotlinx.coroutines.delay
import java.time.Instant

internal data class TimerReadout(val label: String, val progress: Float? = null)

@Composable
internal fun timerReadout(timer: Entity?): TimerReadout {
    if (timer?.available != true) return TimerReadout("Unavailable")
    val seconds = when (timer.state) {
        "active" -> timer.attributes.text("finishes_at")
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?.let { activeSecondsRemaining(it) }
        "paused" -> timerDuration(timer.attributes.text("remaining"))
        else -> null
    }
    val progress = if (timer.state == "active" || timer.state == "paused") {
        val total = timerDuration(timer.attributes.text("duration"))
        if (seconds != null && total != null && total > 0) (seconds.toFloat() / total).coerceIn(0f, 1f) else null
    } else null
    return when (timer.state) {
        "active" -> TimerReadout(seconds?.let { "${clockDuration(it)} left" } ?: "Running", progress)
        "paused" -> TimerReadout(seconds?.let { "Paused · ${clockDuration(it)}" } ?: "Paused", progress)
        else -> TimerReadout("Idle")
    }
}

@Composable
private fun activeSecondsRemaining(deadline: Long): Long {
    var remaining by remember(deadline) { mutableLongStateOf(timerSecondsRemaining(deadline, System.currentTimeMillis())) }
    LaunchedEffect(deadline) {
        while (true) {
            val now = System.currentTimeMillis()
            remaining = timerSecondsRemaining(deadline, now)
            if (remaining == 0L) break
            delay((deadline - now) % 1_000 + 1)
        }
    }
    return remaining
}

internal fun timerSecondsRemaining(deadline: Long, now: Long): Long = ((deadline - now).coerceAtLeast(0) + 999) / 1000

private fun timerDuration(value: String?): Long? {
    val parts = value?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
    if (parts.size != 3 || parts[0] !in 0..100_000 || parts[1] !in 0..59 || parts[2] !in 0..59) return null
    return parts[0] * 3600 + parts[1] * 60 + parts[2]
}

private fun clockDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = (seconds % 60).toString().padStart(2, '0')
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$remainder" else "$minutes:$remainder"
}
