package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphHistory
import app.flickpanel.ha.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun GraphTile(
    tile: TileSpec, label: String, entity: Entity?, link: Link, history: GraphHistory?,
    loadHistory: suspend (String) -> Unit, modifier: Modifier, compact: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val accent = tileAccentColour(tile.accent)
    val graph = history?.takeIf { it.points.isNotEmpty() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (link == Link.ONLINE) LaunchedEffect(tile.entity, loadHistory, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                loadHistory(tile.entity)
                delay(60_000)
            }
        }
    }
    Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
        if (compact) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = tileContentPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    TileTitle(label, maxLines = 1)
                    GraphReading(entity)
                }
                if (graph != null) Sparkline(graph, accent, Modifier.weight(.85f).height(36.dp))
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TileTitle(label, Modifier.weight(1f), maxLines = 1)
                    if (graph != null) Text("6 h", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                GraphReading(entity, prominent = true)
                if (graph != null) Sparkline(graph, accent, Modifier.fillMaxWidth().height(38.dp))
                else TileStatus(
                    when {
                        link != Link.ONLINE -> "History unavailable"
                        history != null -> "No history"
                        else -> "Loading history…"
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GraphReading(entity: Entity?, prominent: Boolean = false) {
    val value = remember(entity) { displayValue(entity, includeUnit = false) }
    if (entity?.available != true) {
        TileStatus(value, maxLines = 1)
        return
    }
    val type = MaterialTheme.typography
    val valueStyle = if (prominent) type.headlineSmall else type.titleLarge
    val unitColour = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = entity.unit
    Text(
        buildAnnotatedString {
            append(value)
            if (unit.isNotBlank()) withStyle(SpanStyle(color = unitColour, fontSize = type.bodyMedium.fontSize)) {
                append(" ")
                append(unit)
            }
        },
        style = valueStyle.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "'tnum'"),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Sparkline(history: GraphHistory, accent: Color, modifier: Modifier) {
    Spacer(modifier.semantics { contentDescription = "Last six hours" }.drawWithCache {
        val points = history.points
        val low = points.minOf { it.value }
        val high = points.maxOf { it.value }
        val span = high - low
        val elapsed = (history.end - history.start).coerceAtLeast(1)
        val inset = minOf(4.dp.toPx(), size.width / 2, size.height / 2)
        val right = size.width - inset
        val bottom = size.height - inset
        val curve = Path()
        var start = 0f
        var lastY = 0f
        for ((index, point) in points.withIndex()) {
            val time = ((point.time.toDouble() - history.start) / elapsed).coerceIn(0.0, 1.0).toFloat()
            val x = inset + time * (right - inset)
            val level = when {
                span == 0.0 -> .5
                span.isFinite() -> (point.value - low) / span
                else -> (point.value / 2 - low / 2) / (high / 2 - low / 2)
            }.toFloat()
            lastY = bottom - level * (bottom - inset)
            if (index == 0) {
                start = x
                curve.moveTo(x, lastY)
            } else curve.lineTo(x, lastY)
        }
        curve.lineTo(right, lastY)
        val fill = Path().apply {
            addPath(curve)
            lineTo(right, bottom)
            lineTo(start, bottom)
            close()
        }
        val wash = Brush.verticalGradient(listOf(accent.copy(alpha = .22f), Color.Transparent))
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            drawPath(fill, wash)
            drawPath(curve, accent, style = stroke)
            drawCircle(accent, 2.75.dp.toPx(), Offset(right, lastY))
        }
    })
}
