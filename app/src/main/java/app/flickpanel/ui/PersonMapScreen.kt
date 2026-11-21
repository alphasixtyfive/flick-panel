package app.flickpanel.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.ha.PersonLocation
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val initialZoom = 16f
private const val maximumZoom = 128f

@Composable
internal fun PersonMapScreen(title: String, location: PersonLocation?, close: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Offline map · Country and city overview", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (location == null) EmptyState("Location unavailable", null)
                else LoadPersonMap(location)
            }
            Text("Made with Natural Earth", Modifier.padding(start = 6.dp, top = 6.dp),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LoadPersonMap(location: PersonLocation) {
    val assets = LocalContext.current.assets
    var basemap by remember(assets) { mutableStateOf<OfflineBasemap?>(null) }
    var loadFailed by remember(assets) { mutableStateOf(false) }
    LaunchedEffect(assets) {
        try {
            basemap = withContext(Dispatchers.IO) { loadOfflineBasemap(assets) }
        } catch (_: IOException) {
            loadFailed = true
        }
    }
    val map = basemap
    when {
        map != null -> InteractivePersonMap(map, location)
        loadFailed -> EmptyState("Map unavailable", null)
        else -> CircularProgressIndicator()
    }
}

@Composable
private fun InteractivePersonMap(map: OfflineBasemap, location: PersonLocation) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val pin = Offset((location.longitude + 180).toFloat(), (90 - location.latitude).toFloat())
    var center by remember { mutableStateOf(pin) }
    var zoom by remember { mutableFloatStateOf(initialZoom) }
    var followsPerson by remember { mutableStateOf(true) }
    val textPaint = remember(colors.onSurfaceVariant, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onSurfaceVariant.toArgb()
            textSize = with(density) { 12.sp.toPx() }
        }
    }
    LaunchedEffect(pin, followsPerson) {
        if (followsPerson) center = pin
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Map showing the person's location" }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    val fit = fitScale(size.width.toFloat(), size.height.toFloat())
                    val screenCenter = Offset(size.width / 2f, size.height / 2f)
                    val anchor = center + (centroid - screenCenter) / (fit * zoom)
                    val nextZoom = (zoom * zoomChange).coerceIn(1f, maximumZoom)
                    val nextCenter = anchor - (centroid + pan - screenCenter) / (fit * nextZoom)
                    followsPerson = false
                    center = Offset(wrapLongitude(nextCenter.x), nextCenter.y.coerceIn(0f, 180f))
                    zoom = nextZoom
                }
            }) {
            val scale = fitScale(size.width, size.height) * zoom
            val start = this.center - center * scale
            val worldWidth = 360f * scale
            for (world in -1..1) {
                val left = start.x + world * worldWidth
                if (left > size.width || left + worldWidth < 0f) continue
                withTransform({
                    translate(left, start.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawPath(map.countries, colors.surfaceContainerHigh)
                    drawPath(map.lakes, colors.surfaceContainerLowest)
                    drawPath(map.countries, colors.outline, style = Stroke(width = .75.dp.toPx() / scale))
                }
            }
            val marker = screenPoint(pin, center, scale)
            drawPlaces(map.places, center, scale, zoom, marker, textPaint)
            drawCircle(colors.surface, radius = 11.dp.toPx(), center = marker)
            drawCircle(colors.primary, radius = 8.dp.toPx(), center = marker)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(onClick = { zoom = (zoom * 1.6f).coerceAtMost(maximumZoom) }, enabled = zoom < maximumZoom) {
                Icon(Icons.Rounded.Add, "Zoom in")
            }
            FilledTonalIconButton(onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) }, enabled = zoom > 1f) {
                Icon(Icons.Rounded.Remove, "Zoom out")
            }
            FilledTonalIconButton(onClick = { followsPerson = true; center = pin }) {
                Icon(Icons.Rounded.MyLocation, "Centre on person")
            }
        }
    }
}

private fun DrawScope.drawPlaces(
    places: List<BasemapPlace>, worldCenter: Offset, scale: Float, zoom: Float, marker: Offset, paint: Paint,
) {
    val rankLimit = when {
        zoom < 2f -> 1
        zoom < 6f -> 3
        zoom < 12f -> 5
        else -> Int.MAX_VALUE
    }
    val safeArea = Rect(0f, 0f, (size.width - 60.dp.toPx()).coerceAtLeast(0f), size.height)
    val occupied = mutableListOf<Rect>()
    for (place in places) {
        if (place.rank > rankLimit || occupied.size == 18) break
        val point = screenPoint(place.point, worldCenter, scale)
        if (!safeArea.contains(point)) continue
        val x = point.x + 4.dp.toPx()
        val textBox = Rect(x, point.y + paint.ascent(), x + paint.measureText(place.name), point.y + paint.descent())
        if (!safeArea.contains(textBox.topRight) || !safeArea.contains(textBox.bottomLeft) ||
            textBox.inflate(12.dp.toPx()).contains(marker) || occupied.any { it.overlaps(textBox) }
        ) continue
        drawCircle(androidx.compose.ui.graphics.Color(paint.color), radius = 1.6.dp.toPx(), center = point)
        drawContext.canvas.nativeCanvas.drawText(place.name, x, point.y, paint)
        occupied.add(textBox.inflate(4.dp.toPx()))
    }
}

private fun DrawScope.screenPoint(point: Offset, worldCenter: Offset, scale: Float): Offset {
    val dx = wrapLongitude(point.x - worldCenter.x + 180f) - 180f
    return center + Offset(dx, point.y - worldCenter.y) * scale
}

private fun wrapLongitude(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun fitScale(width: Float, height: Float) = minOf(width / 360f, height / 180f).coerceAtLeast(.0001f)
