package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.Link
import app.flickpanel.ha.binaryStatus
import java.text.NumberFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ValueTile(
    tile: TileSpec, label: String, entity: Entity?, link: Link, history: List<GraphPoint>?,
    loadHistory: suspend (String) -> Unit, modifier: Modifier, wideGraph: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val trend = tile.kind == TileKind.TREND
    val points = if (trend) history.orEmpty() else emptyList()
    if (trend && link == Link.ONLINE) LaunchedEffect(tile.entity, loadHistory) {
        while (isActive) {
            loadHistory(tile.entity)
            delay(60_000)
        }
    }
    Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TileTitle(label, Modifier.weight(1f))
                if (points.size > 1) Text("6 h", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            val value = displayValue(entity)
            if (entity?.available == true) TileValue(value, prominent = entity.state.toDoubleOrNull()?.isFinite() == true, maxLines = 2)
            else TileStatus(value)
            if (trend) {
                if (points.size > 1) {
                    val chart = if (wideGraph) Modifier.weight(1f) else Modifier.height(21.dp)
                    Sparkline(points, chart.fillMaxWidth())
                } else {
                    Text(
                        when {
                            link != Link.ONLINE -> "History unavailable"
                            history != null -> "No history"
                            else -> "Loading history…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else if (link == Link.OFFLINE && entity?.available == true) {
                TileStatus("Last reported")
            }
        }
    }
}

internal fun displayValue(entity: Entity?): String {
    if (entity == null || !entity.available) return "Unavailable"
    entity.binaryStatus()?.let { return it }
    val value = entity.state.take(120)
    return if (entity.unit.isBlank()) value.replaceFirstChar { it.uppercase() }
    else {
        val precision = when (entity.unit) {
            "°C", "°F" -> 1
            "W", "VA" -> 0
            else -> 3
        }
        val formatted = value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { formatNumber(it, precision) } ?: value
        "$formatted ${entity.unit}"
    }
}

internal fun formatNumber(value: Double, precision: Int): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = precision
        isGroupingUsed = false
    }.format(value)

@Composable
private fun Sparkline(points: List<GraphPoint>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Spacer(modifier.semantics { contentDescription = "Last six hours" }.drawWithCache {
        val first = points.first().time
        val elapsed = (points.last().time - first).coerceAtLeast(1)
        val low = points.minOf { it.value }
        val range = (points.maxOf { it.value } - low).coerceAtLeast(.01)
        val horizontalInset = minOf(2.dp.toPx(), size.width / 2)
        val verticalInset = minOf(4.dp.toPx(), size.height / 2)
        val curve = Path()
        for ((index, point) in points.withIndex()) {
            val x = ((point.time - first).toDouble() / elapsed).toFloat() * (size.width - 2 * horizontalInset) + horizontalInset
            val y = size.height - (((point.value - low) / range).toFloat() * (size.height - 2 * verticalInset) + verticalInset)
            if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind { drawPath(curve, color, style = stroke) }
    })
}
