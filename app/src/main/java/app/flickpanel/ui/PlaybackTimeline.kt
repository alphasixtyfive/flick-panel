package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.PlaybackProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

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
            MediaSlider(progress, "Playback position", enabled, change = { drag = it }) {
                val dragged = drag
                drag = null
                dragged?.let { seek(it * timeline.duration) }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(48.dp).semantics {
                    contentDescription = "Playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
                contentAlignment = Alignment.Center,
            ) { MediaTrack(progress, enabled = true, showThumb = false) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSlider(value: Float, label: String, enabled: Boolean, change: (Float) -> Unit, finish: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Slider(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
        enabled = enabled,
        interactionSource = interaction,
        onValueChangeFinished = finish,
        thumb = { Spacer(Modifier.size(width = 14.dp, height = 24.dp)) },
        track = { MediaTrack(it.value, enabled, showThumb = true, highlight = dragging || focused) },
    )
}

@Composable
private fun MediaTrack(progress: Float, enabled: Boolean, showThumb: Boolean, highlight: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val active = if (enabled) colors.primary else colors.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val y = center.y
        val rtl = layoutDirection == LayoutDirection.Rtl
        val start = Offset(if (rtl) size.width else 0f, y)
        val end = Offset(if (rtl) 0f else size.width, y)
        val thumb = Offset(start.x + (end.x - start.x) * progress.coerceIn(0f, 1f), y)
        val stroke = 4.dp.toPx()
        drawLine(colors.outlineVariant, start, end, stroke, StrokeCap.Round)
        if (progress > 0f) drawLine(active, start, thumb, stroke, StrokeCap.Round)
        if (showThumb) {
            if (highlight) drawCircle(active.copy(alpha = .22f), 12.dp.toPx(), thumb)
            drawCircle(active, 7.dp.toPx(), thumb)
        }
    }
}
