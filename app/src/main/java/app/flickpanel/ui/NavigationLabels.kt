package app.flickpanel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberNavigationLabelWidths(labels: List<String>, style: TextStyle, padding: Dp): List<Dp> {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(labels, style, padding, density, measurer) {
        labels.map { label ->
            val size = measurer.measure(label, style = style, maxLines = 1).size
            maxOf(48.dp, with(density) { size.width.toDp() } + padding * 2)
        }
    }
}
