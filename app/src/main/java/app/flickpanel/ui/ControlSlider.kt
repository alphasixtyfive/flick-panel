package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlSlider(
    value: Float, label: String, enabled: Boolean, change: (Float) -> Unit, finish: () -> Unit,
    modifier: Modifier = Modifier, stateLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Slider(
        value = value,
        onValueChange = change,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            contentDescription = label
            if (stateLabel != null) stateDescription = stateLabel
        },
        enabled = enabled,
        interactionSource = interaction,
        onValueChangeFinished = finish,
        thumb = { Spacer(Modifier.size(width = 14.dp, height = 48.dp)) },
        track = { ControlSliderTrack(it.value, enabled, showThumb = true, highlight = dragging || focused) },
    )
}

@Composable
internal fun ControlSliderTrack(progress: Float, enabled: Boolean, showThumb: Boolean, highlight: Boolean = false) {
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
