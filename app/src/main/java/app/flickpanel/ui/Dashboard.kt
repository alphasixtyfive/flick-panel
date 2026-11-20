package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
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
import java.text.NumberFormat
import kotlin.math.roundToInt

internal val panelCardShape = RoundedCornerShape(26.dp)
internal const val tabletWidthDp = 840
private val cellHeight = 132.dp
private val tileGap = 14.dp

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel) {
    val colors = MaterialTheme.colorScheme
    val now = clockTime()
    val weather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= tabletWidthDp.dp
        val compactNavigation = maxWidth < 600.dp
        val cameraColumns = if (maxWidth >= 1100.dp) 4 else if (wide) 3 else 2
        val side = if (wide) panelWidePadding else 20.dp
        Column(Modifier.widthIn(max = panelMaxWidth).fillMaxSize().padding(horizontal = side)) {
            Row(Modifier.fillMaxWidth().padding(top = if (wide) 34.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.page == Page.ROOMS) RoomTitle(model, wide, Modifier.weight(1f, fill = false))
                else Text("Home", style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClickLabel = "Unlock setup", onLongClick = model::showUnlock)
                        .heightIn(min = 48.dp).semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 34.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (weather != null) {
                        WeatherSummary(state.entity(weather.entity), compact = !wide, prominent = wide)
                    }
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge)
                }
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
            DashboardNavigation(state.page, wide, compactNavigation, model::selectPage)
            key(state.page) {
                val body = Modifier.fillMaxSize().padding(top = 22.dp)
                when (state.page) {
                    Page.CAMERAS -> CameraGallery(state, model, cameraColumns, body)
                    Page.ROOMS -> Rooms(state, model, wide, body)
                    Page.FAVOURITES -> Column(body.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(tileGap)) { Favourites(state, model, wide) }
                }
            }
        }
    }
}

@Composable
private fun DashboardNavigation(current: Page, wide: Boolean, compact: Boolean, selectPage: (Page) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scroll = if (compact) Modifier else Modifier.horizontalScroll(rememberScrollState())
    Row(Modifier.fillMaxWidth().padding(top = if (wide) 28.dp else 20.dp)
        .then(scroll).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (page in Page.entries) {
            val icon = when (page) {
                Page.FAVOURITES -> Icons.Rounded.FavoriteBorder
                Page.ROOMS -> Icons.Rounded.MeetingRoom
                Page.CAMERAS -> Icons.Rounded.Videocam
            }
            val selected = current == page
            Surface(selected = selected, onClick = { selectPage(page) }, shape = CircleShape,
                color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = (if (compact) Modifier.weight(1f) else Modifier).semantics { role = Role.Tab }) {
                if (compact) {
                    Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(page.label, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(24.dp))
                        Text(page.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Favourites(state: PanelUi, model: PanelViewModel, wide: Boolean) {
    val headerWeather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
    val tiles = state.config.favourites.filter { it.showOnHome && it != headerWeather }
    if (tiles.isEmpty()) {
        EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.")
        return
    }
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

@Composable
private fun CameraGallery(state: PanelUi, model: PanelViewModel, columns: Int, modifier: Modifier) {
    val cameras = state.config.favourites.filter { it.kind == TileKind.CAMERA }.sortedByDescending { it.showOnHome }
    if (cameras.isEmpty()) {
        Column(modifier) { EmptyState("No cameras pinned", "Add a camera in setup.") }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(columns), modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tileGap), verticalArrangement = Arrangement.spacedBy(tileGap),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        items(cameras, key = { it.entity }) { camera ->
            CameraTile(camera, state, model, Modifier.fillMaxWidth(), widescreen = true)
        }
    }
}

@Composable
internal fun EmptyState(title: String, note: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        note?.let { Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
internal fun TileGrid(tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int) {
    val rows = remember(tiles, columns) { packRows(tiles, columns) }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
        for (row in rows) TileRow(row, state, model, columns)
    }
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
