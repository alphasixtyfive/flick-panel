package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.Link
import java.text.NumberFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

internal val panelCardShape = RoundedCornerShape(26.dp)
internal val tileGap = 14.dp
private val cellHeight = 132.dp

@Composable
internal fun TileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int) {
    val rows = remember(tiles, columns) { packRows(tiles, columns) }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
        for (row in rows) TileRow(row, state, model, columns)
    }
}

@Composable
internal fun FavouritesTileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, wide: Boolean) {
    val camera = tiles.firstOrNull { it.kind == TileKind.CAMERA }
    val controls = tiles.filterNot { it == camera }
    val rows = remember(controls) { packRows(controls, 2) }
    if (wide && camera != null && rows.size >= 2) {
        val featureRows = rows.take(if (rows.size > 3) 2 else 3)
        Layout(modifier = Modifier.fillMaxWidth(), content = {
            Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
                for (row in featureRows) TileRow(row, state, model, 2)
            }
            CameraTile(camera, state, model, Modifier.fillMaxSize())
        }) { children, constraints ->
            val gap = tileGap.roundToPx()
            val width = constraints.maxWidth
            val controlsWidth = (width - gap) / 2
            val grid = children[0].measure(Constraints.fixedWidth(controlsWidth))
            val preview = children[1].measure(Constraints.fixed(width - controlsWidth - gap, grid.height))
            layout(width, grid.height) {
                grid.placeRelative(0, 0)
                preview.placeRelative(controlsWidth + gap, 0)
            }
        }
        TileGrid(rows.drop(featureRows.size).flatten(), state, model, 4)
    } else {
        TileGrid(tiles, state, model, if (wide) 4 else 2)
    }
}

private fun span(tile: TileSpec) = when (tile.kind) {
    TileKind.SLIDER, TileKind.CAMERA, TileKind.CLIMATE, TileKind.MEDIA -> 2
    else -> 1
}

private fun packRows(tiles: List<TileSpec>, columns: Int): List<List<TileSpec>> {
    val result = mutableListOf<MutableList<TileSpec>>()
    var used = columns
    for (tile in tiles) {
        val width = span(tile)
        if (used + width > columns) { result.add(mutableListOf()); used = 0 }
        result.last().add(tile)
        used += width
    }
    return result
}

@Composable
private fun TileRow(row: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        for (tile in row) key(tile.entity) {
            val minimum = when (tile.kind) {
                TileKind.APP -> 76.dp
                TileKind.CAMERA -> 226.dp
                else -> cellHeight
            }
            val modifier = Modifier.heightIn(min = minimum)
            if (tile.kind == TileKind.CAMERA) CameraTile(tile, state, model, modifier)
            else ControlTile(tile, state, model, modifier)
        }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val gap = tileGap.roundToPx().coerceAtMost(width / columns)
        val cells = width - gap * (columns - 1)
        var column = 0
        val slots = row.map { tile ->
            val start = cells * column / columns + gap * column
            column += span(tile)
            val end = cells * column / columns + gap * (column - 1)
            start to (end - start)
        }
        val height = constraints.constrainHeight(children.indices.maxOf { children[it].minIntrinsicHeight(slots[it].second) })
        val measured = children.mapIndexed { index, child -> child.measure(Constraints.fixed(slots[index].second, height)) }
        layout(width, height) {
            measured.forEachIndexed { index, child -> child.placeRelative(slots[index].first, 0) }
        }
    }
}

@Composable
private fun ControlTile(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier) {
    val entity = state.entity(tile.entity)
    val label = state.label(tile)
    val pending = state.pending[tile.entity]
    val live = state.entities[tile.entity]
    val ready = state.link == Link.ONLINE && live?.available == true && pending == null
    val colors = MaterialTheme.colorScheme
    val on = entity?.state == "on"
    when (tile.kind) {
        TileKind.TOGGLE, TileKind.ACTION -> {
            val action = tile.kind == TileKind.ACTION
            val running = tile.statusEntity?.let(state.entities::get)?.state == "active"
            val active = if (action) running else on
            val text = pending ?: when {
                !ready -> "Unavailable"
                running -> "Running"
                action -> "Run"
                on -> "On"
                else -> "Off"
            }
            Surface(onClick = {
                if (live != null) { if (action) model.action(live) else model.toggle(live, !on) }
            }, enabled = ready, modifier = modifier.semantics {
                stateDescription = text
            }, shape = panelCardShape, color = if (active) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (active) colors.onPrimaryContainer else colors.onSurface) {
                Column(Modifier.fillMaxSize().padding(19.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(if (action) Icons.Rounded.PlayArrow else Icons.Rounded.Lightbulb, null,
                            tint = if (active) colors.onPrimaryContainer else colors.primary, modifier = Modifier.size(29.dp))
                    }
                    Text(text, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        TileKind.SLIDER -> {
            Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
                Column(Modifier.padding(horizontal = 19.dp, vertical = 17.dp)) {
                    BrightnessControl(label, entity, live, pending, ready, model, Modifier.padding(top = 7.dp)) { level ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entity?.available == true) "$level%" else "—", style = MaterialTheme.typography.titleMedium,
                                color = colors.primary)
                        }
                    }
                }
            }
        }
        TileKind.CLIMATE -> Climate(tile, state, model, modifier, live, ready)
        TileKind.MEDIA -> MusicTile(label, entity, ready, model, modifier)
        TileKind.WEATHER -> Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WeatherSummary(entity, compact = false)
            }
        }
        TileKind.VALUE, TileKind.TREND -> {
            val trend = tile.kind == TileKind.TREND
            if (trend && state.link == Link.ONLINE) LaunchedEffect(tile.entity) {
                while (isActive) { model.loadGraph(tile.entity); delay(60_000) }
            }
            Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (trend) Text("6 h", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    Text(displayValue(entity), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (trend) {
                        val points = state.graphs[tile.entity].orEmpty()
                        if (points.size > 1) Sparkline(points, Modifier.fillMaxWidth().height(21.dp))
                        else Text(if (state.graphs.containsKey(tile.entity)) "No history" else "History", style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant)
                    } else if (state.link == Link.OFFLINE && entity?.available == true) {
                        Text("Last reported", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        TileKind.APP -> AppTile(tile, modifier)
        TileKind.CAMERA -> Unit
    }
}

@Composable
private fun BrightnessControl(
    label: String,
    entity: Entity?,
    live: Entity?,
    pending: String?,
    enabled: Boolean,
    model: PanelViewModel,
    modifier: Modifier = Modifier,
    heading: @Composable (Int) -> Unit,
) {
    var draggingLevel by remember { mutableStateOf<Float?>(null) }
    val shown = draggingLevel?.roundToInt() ?: pending?.removeSuffix("%")?.toIntOrNull() ?: entity?.brightness ?: 0
    heading(shown)
    Slider(value = shown.toFloat(), valueRange = 0f..100f, enabled = enabled,
        onValueChange = { draggingLevel = it },
        onValueChangeFinished = {
            draggingLevel?.let { if (live != null) model.dim(live, it.roundToInt()) }
            draggingLevel = null
        }, modifier = modifier.fillMaxWidth().semantics { contentDescription = "$label brightness" })
}

private fun displayValue(entity: Entity?): String {
    if (entity == null || !entity.available) return "Unavailable"
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
private fun Climate(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, entity: Entity?, enabled: Boolean) {
    val info = entity ?: state.entity(tile.entity)
    val current = info?.attributes?.optDouble("current_temperature")?.takeIf(Double::isFinite)
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(19.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.label(tile), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(current?.let { formatNumber(it, 1) + "°" } ?: "—", style = MaterialTheme.typography.headlineMedium)
            }
            TemperatureSetpoint(info, entity, state.pending[tile.entity], enabled, model, gap = 9.dp)
        }
    }
}

@Composable
private fun TemperatureSetpoint(
    info: Entity?,
    live: Entity?,
    pending: String?,
    enabled: Boolean,
    model: PanelViewModel,
    gap: Dp,
) {
    val attrs = info?.attributes
    val target = attrs?.optDouble("temperature")?.takeIf(Double::isFinite)
    val step = attrs?.optDouble("target_temp_step", .5)?.takeIf { it > 0 } ?: .5
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target - step) },
            enabled = enabled && target != null) { Icon(Icons.Rounded.Remove, "Lower target") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pending ?: target?.let { "$it°" } ?: "—")
            Text("Target", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target + step) },
            enabled = enabled && target != null) { Icon(Icons.Rounded.Add, "Raise target") }
    }
}

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
