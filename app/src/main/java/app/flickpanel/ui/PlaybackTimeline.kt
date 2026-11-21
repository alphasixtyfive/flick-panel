package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.PlaybackProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackTimeline(
    timeline: PlaybackProgress, track: String, canSeek: Boolean, enabled: Boolean, seek: (Double) -> Unit,
) {
    val advancing = timeline.advancingSince
    var now by remember(advancing) { mutableStateOf(Instant.now()) }
    LaunchedEffect(advancing) {
        if (advancing == null) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            now = Instant.now()
        }
    }
    var drag by remember(track) { mutableStateOf<Float?>(null) }
    val position = drag?.let { it * timeline.duration } ?: timeline.positionAt(now)
    val progress = (position / timeline.duration).toFloat()
    val colors = MaterialTheme.colorScheme
    Column {
        if (canSeek) {
            val interaction = remember { MutableInteractionSource() }
            Slider(
                value = progress,
                onValueChange = { drag = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    contentDescription = "Playback position"
                },
                enabled = enabled,
                interactionSource = interaction,
                onValueChangeFinished = {
                    val dragged = drag
                    drag = null
                    dragged?.let { seek(it * timeline.duration) }
                },
                thumb = { SliderDefaults.Thumb(interaction, enabled = enabled, thumbSize = DpSize(12.dp, 12.dp)) },
                track = { PlaybackTrack(it.value, enabled) },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(48.dp).semantics {
                    contentDescription = "Playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
                contentAlignment = Alignment.Center,
            ) { PlaybackTrack(progress, enabled = true) }
        }
        val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "'tnum'")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackTime(position), color = colors.onSurfaceVariant, style = style)
            Text(playbackTime(timeline.duration), color = colors.onSurfaceVariant, style = style)
        }
    }
}

private fun playbackTime(seconds: Double): String {
    val total = seconds.toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
private fun PlaybackTrack(progress: Float, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.outlineVariant)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
            .background(if (enabled) colors.primary else colors.onSurfaceVariant))
    }
}
