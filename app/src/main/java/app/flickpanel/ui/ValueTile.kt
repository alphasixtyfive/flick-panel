package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
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
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, wideGraph: Boolean,
) {
    val label = state.label(tile)
    val entity = state.entity(tile.entity)
    val colors = MaterialTheme.colorScheme
    val trend = tile.kind == TileKind.TREND
    if (trend && state.link == Link.ONLINE) LaunchedEffect(tile.entity) {
        while (isActive) {
            model.loadGraph(tile.entity)
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
                if (trend) Text("6 h", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            val value = displayValue(entity)
            if (entity?.available == true) TileValue(value, prominent = entity.state.toDoubleOrNull()?.isFinite() == true, maxLines = 2)
            else TileStatus(value)
            if (trend) {
                val points = state.graphs[tile.entity].orEmpty()
                if (points.size > 1) {
                    val chart = if (wideGraph) Modifier.weight(1f) else Modifier.height(21.dp)
                    Sparkline(points, chart.fillMaxWidth())
                } else {
                    Text(
                        when {
                            state.link != Link.ONLINE -> "History unavailable"
                            state.graphs.containsKey(tile.entity) -> "No history"
                            else -> "Loading history…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else if (state.link == Link.OFFLINE && entity?.available == true) {
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
    val first = points.first().time
    val elapsed = (points.last().time - first).coerceAtLeast(1)
    val low = points.minOf { it.value }
    val range = (points.maxOf { it.value } - low).coerceAtLeast(.01)
    Canvas(modifier.semantics { contentDescription = "Last six hours" }) {
        val curve = Path()
        for ((index, point) in points.withIndex()) {
            val x = ((point.time - first).toDouble() / elapsed).toFloat() * (size.width - 4f) + 2f
            val y = size.height - (((point.value - low) / range).toFloat() * (size.height - 8f) + 4f)
            if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        drawPath(curve, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
