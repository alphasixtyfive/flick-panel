package app.flickpanel.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

@Composable
internal fun Modifier.tileSlider(
    enabled: Boolean, level: Int, preview: MutableState<Int?>, step: Int, commit: (Int) -> Unit,
): Modifier {
    val direction = LocalLayoutDirection.current
    val submit by rememberUpdatedState(commit)
    if (!enabled) return this
    return pointerInput(direction, preview) {
        fun levelAt(x: Float): Int {
            val fraction = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            return ((if (direction == LayoutDirection.Ltr) fraction else 1f - fraction) * 100).roundToInt()
        }
        try {
            detectHorizontalDragGestures(
                onDragStart = { preview.value = levelAt(it.x) },
                onHorizontalDrag = { change, _ -> preview.value = levelAt(change.position.x) },
                onDragEnd = {
                    val value = preview.value
                    preview.value = null
                    if (value != null) submit(value)
                },
                onDragCancel = { preview.value = null },
            )
        } finally {
            preview.value = null
        }
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val delta = when (event.key) {
            Key.DirectionRight -> step
            Key.DirectionLeft -> -step
            else -> return@onKeyEvent false
        }
        val target = (level + if (direction == LayoutDirection.Ltr) delta else -delta).coerceIn(0, 100)
        if (target != level) submit(target)
        target != level
    }.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), 0f..100f)
        setProgress {
            if (!it.isFinite()) false else {
                submit(it.roundToInt().coerceIn(0, 100))
                true
            }
        }
    }
}

internal fun Modifier.tileFill(fraction: Float, color: Color): Modifier {
    if (!fraction.isFinite() || fraction <= 0f) return this
    return drawBehind {
        val width = size.width * fraction.coerceAtMost(1f)
        val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
        drawRect(color, Offset(left, 0f), Size(width, size.height))
    }
}
