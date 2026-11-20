package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val cellShape = RoundedCornerShape(25.dp)
private val cellHeight = 123.dp

@Composable
private fun clockTime(): ZonedDateTime {
    val now by produceState(ZonedDateTime.now()) {
        while (isActive) {
            value = ZonedDateTime.now()
            delay(60_025 - System.currentTimeMillis() % 60_000)
        }
    }
    return now
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AmbientClock(message: String?, wake: () -> Unit) {
    val now = clockTime()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff10151b))
        .combinedClickable(onClickLabel = "Open home controls", onClick = wake)) {
        if (message != null) {
            Text(message, color = Color(0xffc1d1e1), fontSize = 23.sp, textAlign = TextAlign.Center,
                maxLines = 2, modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 34.dp, vertical = 34.dp))
        }
        val size = (maxWidth.value * .18f).coerceIn(68f, 160f).sp
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = size, lineHeight = size,
                fontWeight = FontWeight.Medium, color = Color(0xffdce7f3))
            Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.titleLarge,
                color = Color(0xffa9baca), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel) {
    val colors = MaterialTheme.colorScheme
    val now = clockTime()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val side = if (wide) 36.dp else 19.dp
        Column(Modifier.fillMaxSize().padding(horizontal = side)) {
            Row(Modifier.fillMaxWidth().padding(top = if (wide) 24.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Home", style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClickLabel = "Unlock setup", onLongClick = model::showUnlock))
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant)
            }
            state.message?.let { message ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = colors.primary, modifier = Modifier.size(21.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val status = when (state.link) {
                Link.OFFLINE -> "Home Assistant is offline"
                Link.CONNECTING -> "Connecting to Home Assistant…"
                Link.AUTH_REJECTED -> "Home Assistant rejected the token"
                Link.ONLINE -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            PrimaryScrollableTabRow(
                selectedTabIndex = state.page.ordinal,
                edgePadding = 0.dp,
                modifier = Modifier.padding(top = 9.dp),
                containerColor = colors.surface,
                divider = { HorizontalDivider(color = colors.outlineVariant) }
            ) {
                Page.entries.forEach { page ->
                    Tab(selected = state.page == page, onClick = { model.selectPage(page) }, text = { Text(page.label) })
                }
            }
            key(state.page) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.page) {
                        Page.FAVOURITES -> Favourites(state, model, wide)
                        Page.ROOMS -> Rooms(state, model, wide)
                        Page.CAMERAS -> {
                            val cameras = state.config.favourites.filter { it.kind == TileKind.CAMERA }
                            if (cameras.isEmpty()) EmptyState("No cameras pinned", "Add a camera in setup.")
                            else TileGrid(cameras, state, model, if (wide) 4 else 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Favourites(state: PanelUi, model: PanelViewModel, wide: Boolean) {
    val tiles = state.config.favourites
    if (tiles.isEmpty()) {
        EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.")
        return
    }
    val camera = tiles.firstOrNull { it.kind == TileKind.CAMERA }
    val controls = tiles.filterNot { it == camera }
    if (wide && camera != null && controls.isNotEmpty()) {
        val rows = remember(controls) { packRows(controls, 2).size.coerceIn(1, 3) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TileGrid(controls, state, model, 2)
            }
            CameraTile(camera, state, model, Modifier.weight(1f).height(cellHeight * rows + 12.dp * (rows - 1)))
        }
    } else {
        TileGrid(tiles, state, model, if (wide) 4 else 2)
    }
}

@Composable
private fun Rooms(state: PanelUi, model: PanelViewModel, wide: Boolean) {
    val rooms = state.rooms
    if (rooms.isEmpty()) {
        val message = if (state.loadingCatalogue || state.link == Link.CONNECTING) "Finding rooms…" else "No rooms to show"
        EmptyState(message, if (state.link == Link.ONLINE && !state.loadingCatalogue) "Assign lights or thermostats to an area in Home Assistant." else null)
        return
    }
    var menu by remember { mutableStateOf(false) }
    val selected = rooms.firstOrNull { it.id == state.room } ?: rooms.first()
    Box {
        FilledTonalButton(onClick = { menu = true }) {
            Text(selected.name)
            Icon(Icons.Rounded.ArrowDropDown, "Choose room")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (room in rooms) DropdownMenuItem(text = { Text(room.name) }, onClick = { menu = false; model.selectRoom(room.id) })
        }
    }
    TileGrid(state.roomTiles, state, model, if (wide) 4 else 2)
}

@Composable
private fun EmptyState(title: String, note: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        note?.let { Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun span(tile: TileSpec) = if (tile.kind in setOf(TileKind.SLIDER, TileKind.CAMERA, TileKind.CLIMATE)) 2 else 1

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
private fun TileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int) {
    val rows = remember(tiles, columns) { packRows(tiles, columns) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in rows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (tile in row) key(tile.entity) {
                val modifier = Modifier.weight(span(tile).toFloat())
                if (tile.kind == TileKind.CAMERA) CameraTile(tile, state, model, modifier.height(207.dp))
                else ControlTile(tile, state, model, modifier.heightIn(min = cellHeight))
            }
            val remaining = columns - row.sumOf(::span)
            if (remaining > 0) Spacer(Modifier.weight(remaining.toFloat()))
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
            }, shape = cellShape, color = if (active) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (active) colors.onPrimaryContainer else colors.onSurface) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(if (action) Icons.Rounded.PlayArrow else Icons.Rounded.Lightbulb, null,
                        tint = if (active) colors.onPrimaryContainer else colors.primary)
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        TileKind.SLIDER -> {
            val reported = entity?.brightness ?: 0
            var dragging by remember(tile.entity) { mutableStateOf(false) }
            var level by remember(tile.entity) { mutableStateOf(0f) }
            val shown = if (dragging) level.roundToInt() else pending?.removeSuffix("%")?.toIntOrNull() ?: reported
            Surface(modifier, shape = cellShape, color = colors.surfaceContainer) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (entity?.available == true) "$shown%" else "—", color = colors.primary)
                    }
                    Slider(value = shown.toFloat(), valueRange = 0f..100f, enabled = ready,
                        onValueChange = { dragging = true; level = it },
                        onValueChangeFinished = {
                            if (dragging && live != null) model.dim(live, level.roundToInt())
                            dragging = false
                        }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp).semantics { contentDescription = "$label brightness" })
                }
            }
        }
        TileKind.CLIMATE -> Climate(tile, state, model, modifier, live, ready)
        TileKind.VALUE, TileKind.TREND -> {
            val trend = tile.kind == TileKind.TREND
            if (trend && state.link == Link.ONLINE) LaunchedEffect(tile.entity) {
                while (isActive) { model.loadGraph(tile.entity); delay(60_000) }
            }
            Surface(modifier, shape = cellShape, color = colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (trend) Text("6 h", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    Text(displayValue(entity), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (trend) {
                        val points = state.graphs[tile.entity].orEmpty()
                        if (points.size > 1) Sparkline(points, Modifier.fillMaxWidth().height(35.dp))
                        else Text(if (state.graphs.containsKey(tile.entity)) "No history" else "History", style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant)
                    } else if (state.link == Link.OFFLINE && entity?.available == true) {
                        Text("Last reported", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        TileKind.CAMERA -> Unit
    }
}

private fun displayValue(entity: Entity?): String {
    if (entity == null || !entity.available) return "Unavailable"
    val value = entity.state.take(120)
    return if (entity.unit.isBlank()) value.replaceFirstChar { it.uppercase() }
    else "$value ${entity.unit}"
}

@Composable
private fun Climate(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, entity: Entity?, enabled: Boolean) {
    val info = entity ?: state.entity(tile.entity)
    val attrs = info?.attributes
    val current = attrs?.optDouble("current_temperature")?.takeIf(Double::isFinite)
    val target = attrs?.optDouble("temperature")?.takeIf(Double::isFinite)
    val step = attrs?.optDouble("target_temp_step", .5)?.takeIf { it > 0 } ?: .5
    Surface(modifier, shape = cellShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.label(tile), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(current?.let { "$it°" } ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
            FilledTonalIconButton(onClick = { if (entity != null && target != null) model.setTemperature(entity, target - step) },
                enabled = enabled && target != null) { Icon(Icons.Rounded.Remove, "Lower target") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.pending[tile.entity] ?: target?.let { "$it°" } ?: "—")
                Text("Target", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalIconButton(onClick = { if (entity != null && target != null) model.setTemperature(entity, target + step) },
                enabled = enabled && target != null) { Icon(Icons.Rounded.Add, "Raise target") }
        }
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
