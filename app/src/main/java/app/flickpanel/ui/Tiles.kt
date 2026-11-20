package app.flickpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
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
import kotlin.math.roundToInt

internal val panelCardShape = RoundedCornerShape(26.dp)
internal val tileGap = 14.dp
private val cellHeight = 132.dp
private val compactCellHeight = 110.dp
private val shortcutHeight = 64.dp

@Composable
internal fun TileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, compact: Boolean = false) {
    val rows = remember(tiles, columns, compact) { packRows(tiles, columns, compact) }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
        for (row in rows) TileRow(row, state, model, columns, compact)
    }
}

@Composable
internal fun FavouritesTileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, wide: Boolean) {
    if (!wide) {
        TileGrid(tiles, state, model, columns = 2)
        return
    }

    val (apps, devices) = remember(tiles) { tiles.partition { it.kind == TileKind.APP } }
    val summary = if (apps.size >= 3) devices.firstOrNull { it.kind == TileKind.TREND } else null
    val controls = remember(devices, summary) { devices.filterNot { it == summary } }
    val camera = controls.firstOrNull { it.kind == TileKind.CAMERA }
    val rows = remember(controls, camera) {
        packRows(controls.filterNot { it.kind == TileKind.CAMERA }, columns = 2, compact = true)
    }
    if (camera != null && rows.size >= 2) {
        val featureRows = rows.take(3)
        FeaturedCamera(camera, featureRows, state, model)
        val remaining = remember(controls, camera, featureRows) {
            val featured = featureRows.flatten().toSet() + camera
            controls.filterNot { it in featured }
        }
        TileGrid(remaining, state, model, columns = 4, compact = true)
    } else {
        TileGrid(controls, state, model, columns = 4, compact = true)
    }
    if (summary != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
            Column(Modifier.weight(1f)) {
                TileGrid(apps.take(4), state, model, columns = 2, compact = true)
            }
            ControlTile(summary, state, model, Modifier.weight(1f).height(shortcutHeight * 2 + tileGap),
                compact = true, wideGraph = true)
        }
        TileGrid(apps.drop(4), state, model, columns = 4, compact = true)
    } else {
        TileGrid(apps, state, model, columns = 4, compact = true)
    }
}

@Composable
private fun FeaturedCamera(camera: TileSpec, rows: List<List<TileSpec>>, state: PanelUi, model: PanelViewModel) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
            for (row in rows) TileRow(row, state, model, columns = 2, compact = true)
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
}

private fun span(tile: TileSpec, compact: Boolean) = when (tile.kind) {
    TileKind.CAMERA -> 2
    TileKind.SLIDER, TileKind.CLIMATE, TileKind.MEDIA -> if (!compact || tile.wideOnTablet) 2 else 1
    else -> if (compact && tile.wideOnTablet) 2 else 1
}

private fun packRows(tiles: List<TileSpec>, columns: Int, compact: Boolean): List<List<TileSpec>> {
    val result = mutableListOf<MutableList<TileSpec>>()
    var used = columns
    for (tile in tiles) {
        val width = span(tile, compact)
        if (used + width > columns) { result.add(mutableListOf()); used = 0 }
        result.last().add(tile)
        used += width
    }
    return result
}

@Composable
private fun TileRow(row: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, compact: Boolean) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        for (tile in row) key(tile.entity) {
            val minimum = when (tile.kind) {
                TileKind.APP -> if (compact) shortcutHeight else 76.dp
                TileKind.CAMERA -> 226.dp
                TileKind.IMAGE -> if (compact && tile.wideOnTablet) 190.dp else 144.dp
                else -> if (compact) compactCellHeight else cellHeight
            }
            val modifier = Modifier.heightIn(min = minimum)
            if (tile.kind == TileKind.CAMERA) CameraTile(tile, state, model, modifier)
            else ControlTile(tile, state, model, modifier, compact)
        }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val gap = tileGap.roundToPx().coerceAtMost(width / columns)
        val cells = width - gap * (columns - 1)
        var column = 0
        val slots = row.map { tile ->
            val start = cells * column / columns + gap * column
            column += span(tile, compact)
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
private fun ControlTile(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, compact: Boolean,
    wideGraph: Boolean = false,
) {
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
            val background by animateColorAsState(
                if (active) colors.primaryContainer else colors.surfaceContainer,
                animationSpec = tween(durationMillis = 140),
                label = "Tile state",
            )
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
            }, shape = panelCardShape, color = background,
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
        TileKind.SLIDER -> BrightnessTile(label, entity, live, pending, ready, model, modifier)
        TileKind.CLIMATE -> Climate(tile, state, model, modifier, live, ready, compact)
        TileKind.MEDIA -> MusicTile(label, entity, ready, model, modifier)
        TileKind.IMAGE -> ImageTile(tile.entity, label, entity?.state, state.link == Link.ONLINE && entity?.available == true, model, modifier)
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
                        if (points.size > 1) {
                            val chart = if (wideGraph) Modifier.weight(1f) else Modifier.height(21.dp)
                            Sparkline(points, chart.fillMaxWidth())
                        }
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
private fun BrightnessTile(
    label: String,
    entity: Entity?,
    live: Entity?,
    pending: String?,
    enabled: Boolean,
    model: PanelViewModel,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    val currentLive by rememberUpdatedState(live)
    var draggingLevel by remember(entity?.id) { mutableStateOf<Int?>(null) }
    val on = entity?.state == "on"
    val level = draggingLevel ?: pending?.removeSuffix("%")?.toIntOrNull() ?: entity?.brightness
    val status = when {
        draggingLevel != null -> "$draggingLevel%"
        pending != null -> pending
        !enabled -> "Unavailable"
        !on -> "Off"
        level != null -> "$level%"
        else -> "On"
    }
    Surface(
        onClick = { live?.let { model.toggle(it, !on) } },
        enabled = enabled,
        modifier = modifier.pointerInput(enabled, direction) {
            if (!enabled) return@pointerInput
            fun levelAt(x: Float): Int {
                val fraction = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                return ((if (direction == LayoutDirection.Ltr) fraction else 1f - fraction) * 100).roundToInt()
            }
            try {
                detectHorizontalDragGestures(
                    onDragStart = { draggingLevel = levelAt(it.x) },
                    onHorizontalDrag = { change, _ -> draggingLevel = levelAt(change.position.x) },
                    onDragEnd = {
                        val target = draggingLevel
                        draggingLevel = null
                        if (target != null) currentLive?.let { model.dim(it, target) }
                    },
                    onDragCancel = { draggingLevel = null },
                )
            } finally {
                draggingLevel = null
            }
        }.semantics {
            stateDescription = status
            progressBarRangeInfo = ProgressBarRangeInfo((level ?: 0).toFloat(), 0f..100f)
            if (enabled && live != null) setProgress { requested ->
                if (!requested.isFinite()) false else {
                    model.dim(live, requested.roundToInt().coerceIn(0, 100))
                    true
                }
            }
        },
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxSize().drawBehind {
                val fillWidth = size.width * (level ?: 0).coerceIn(0, 100) / 100f
                val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - fillWidth
                drawRect(colors.primaryContainer, Offset(left, 0f), Size(fillWidth, size.height))
            }.padding(19.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.Lightbulb, null, tint = colors.primary, modifier = Modifier.size(29.dp))
            }
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun displayValue(entity: Entity?): String {
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
private fun Climate(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier,
    entity: Entity?, enabled: Boolean, compact: Boolean,
) {
    val info = entity ?: state.entity(tile.entity)
    val current = info?.attributes?.optDouble("current_temperature")?.takeIf(Double::isFinite)
    val temperature = current?.let { formatNumber(it, 1) + "°" } ?: "—"
    val label = state.label(tile)
    val pending = state.pending[tile.entity]
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        if (compact) {
            Column(Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(temperature, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                TemperatureSetpoint(info, entity, pending, enabled, model, compact = true)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(19.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(temperature, style = MaterialTheme.typography.headlineMedium)
                }
                TemperatureSetpoint(info, entity, pending, enabled, model, compact = false)
            }
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
    compact: Boolean,
) {
    val attrs = info?.attributes
    val target = attrs?.optDouble("temperature")?.takeIf(Double::isFinite)
    val step = attrs?.optDouble("target_temp_step", .5)?.takeIf { it > 0 } ?: .5
    val adjustable = enabled && live != null && target != null
    Row(
        if (compact) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(if (compact) Modifier.weight(1f) else Modifier.padding(end = 10.dp)) {
            Text("Set to", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(pending ?: target?.let { formatNumber(it, 1) + "°" } ?: "—",
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target - step) },
            enabled = adjustable, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove, "Lower target") }
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target + step) },
            enabled = adjustable, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Raise target") }
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
