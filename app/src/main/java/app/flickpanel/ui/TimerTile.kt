package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity
import app.flickpanel.ha.TimerPhase
import app.flickpanel.ha.timerSecondsRemaining
import app.flickpanel.ha.timerSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class TimerReadout(val label: String, val progress: Float? = null, val running: Boolean = false)

@Composable
internal fun TimerTile(label: String, entity: Entity?, modifier: Modifier, accent: TileAccent?, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val surface = tileContainerColour(accent)
    val fill = tileContainerColour(accent, active = true)
    val countdown = timerReadout(entity)
    Surface(modifier, shape = panelCardShape, color = surface, contentColor = colors.onSurface) {
        Column(
            Modifier.fillMaxSize().tileFill(countdown.progress ?: 0f, fill).padding(tileContentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AccentedTileTitle(label, Icons.Rounded.Timer, accent, customIcon = icon,
                surface = surface, filledSurface = fill)
            TileValue(countdown.label, maxLines = 2)
        }
    }
}

@Composable
internal fun timerReadout(timer: Entity?, idleLabel: String = "Idle", unavailableLabel: String = "Unavailable"): TimerReadout {
    val snapshot = remember(timer) { timer?.timerSnapshot() } ?: return TimerReadout(unavailableLabel)
    if (snapshot.phase == TimerPhase.IDLE) return TimerReadout(idleLabel)
    val active = snapshot.phase == TimerPhase.ACTIVE
    val seconds = if (active) snapshot.endsAtMillis?.let { activeSecondsRemaining(it) } else snapshot.pausedSeconds
    val label = if (active) seconds?.let { "${clockDuration(it)} left" } ?: "Running"
        else seconds?.let { "Paused · ${clockDuration(it)}" } ?: "Paused"
    return TimerReadout(label, snapshot.remainingFraction(seconds), running = true)
}

@Composable
private fun activeSecondsRemaining(deadline: Long): Long {
    var remaining by remember(deadline) { mutableLongStateOf(timerSecondsRemaining(deadline, System.currentTimeMillis())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(deadline, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val now = System.currentTimeMillis()
                remaining = timerSecondsRemaining(deadline, now)
                if (remaining == 0L) break
                delay((deadline - now) % 1_000 + 1)
            }
        }
    }
    return remaining
}

private fun clockDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = (seconds % 60).toString().padStart(2, '0')
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$remainder" else "$minutes:$remainder"
}
