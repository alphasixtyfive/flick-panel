package app.flickpanel.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.ha.PersonLocation

internal data class MapColours(
    val water: Color, val land: Color, val boundary: Color, val park: Color,
    val localRoad: Color, val mainRoad: Color, val label: Color, val pin: Color,
)

@Composable
internal fun mapColours(): MapColours = if (MaterialTheme.colorScheme.surface.luminance() < .45f) {
    MapColours(Color(0xff192f40), Color(0xff29353e), Color(0xff607484), Color(0xff31493d),
        Color(0xff60717d), Color(0xffc0ab7d), Color(0xfff1f5f9), Color(0xff74c1ff))
} else {
    MapColours(Color(0xffb8d9e8), Color(0xffe8e7dc), Color(0xff6e8187), Color(0xffc6dac0),
        Color(0xffa7a397), Color(0xff917951), Color(0xff24323a), Color(0xff155ca3))
}

@Composable
internal fun PersonMapCanvas(
    map: OfflineBasemap,
    detail: OfflineDetail?,
    location: PersonLocation,
    viewport: MapViewport,
    minimumSpan: Float,
    colours: MapColours,
    onTransform: (Offset, Offset, Offset, Float, Float) -> Unit,
) {
    val latestTransform by rememberUpdatedState(onTransform)
    val density = LocalDensity.current
    val paint = remember(colours, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colours.label.toArgb()
            textSize = with(density) { 12.sp.toPx() }
        }
    }
    val halo = remember(paint, colours) {
        Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = with(density) { 3.dp.toPx() }
            strokeJoin = Paint.Join.ROUND
            color = colours.land.toArgb()
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Map showing the person's location" }
        .pointerInput(minimumSpan) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                latestTransform(Offset(size.width / 2f, size.height / 2f), centroid, pan, zoom, minimumSpan)
            }
        }) {
        val scale = viewport.scale(size.height)
        val marker = viewport.screenPoint(location.mapPoint(), center, scale)
        val origin = center - Offset(viewport.centre.x * scale.x, viewport.centre.y * scale.y)
        drawRect(colours.water)
        drawWorld(map, colours, origin, scale)
        val close = viewport.latitudeSpan <= .24f
        if (detail != null && close) {
            val window = Rect(
                viewport.centre - Offset(center.x / scale.x, center.y / scale.y),
                viewport.centre + Offset(center.x / scale.x, center.y / scale.y),
            )
            drawDetail(detail, colours, origin, scale, window, viewport.latitudeSpan)
        }

        val placeRank = when {
            viewport.latitudeSpan > 40f -> 1
            viewport.latitudeSpan > 15f -> 3
            viewport.latitudeSpan > 6f -> 5
            else -> Int.MAX_VALUE
        }
        val places = if (detail != null && viewport.latitudeSpan < .085f) detail.roadNames else map.places
        drawLabels(places, viewport, scale, marker, paint, halo, placeRank)

        val accuracy = location.accuracyMetres
        if (accuracy != null) {
            val radius = (accuracy / metresPerLatitudeDegree * scale.y).toFloat()
                .coerceAtMost(maxOf(size.width, size.height) * 1.25f)
            if (radius > 12.dp.toPx()) {
                drawCircle(colours.pin.copy(alpha = .16f), radius, marker)
                drawCircle(colours.pin.copy(alpha = .62f), radius, marker, style = Stroke(1.dp.toPx()))
            }
        }
        drawCircle(Color(0xff121a21), 12.dp.toPx(), marker)
        drawCircle(Color.White, 9.dp.toPx(), marker)
        drawCircle(colours.pin, 6.5.dp.toPx(), marker)
    }
}

private fun DrawScope.drawWorld(map: OfflineBasemap, colours: MapColours, origin: Offset, scale: Offset) {
    val worldWidth = 360f * scale.x
    val first = kotlin.math.floor(-origin.x / worldWidth).toInt()
    val last = kotlin.math.ceil((size.width - origin.x) / worldWidth).toInt() - 1
    for (world in first..last) {
        withTransform({
            translate(origin.x + world * worldWidth, origin.y)
            scale(scale.x, scale.y, pivot = Offset.Zero)
        }) {
            drawPath(map.countries, colours.land)
            drawPath(map.lakes, colours.water)
            drawPath(map.countries, colours.boundary, style = Stroke(.65.dp.toPx() / scale.y))
        }
    }
}

private fun DrawScope.drawDetail(
    detail: OfflineDetail, colours: MapColours, origin: Offset, scale: Offset, window: Rect, span: Float,
) {
    val visible = detail.cells.filter { it.bounds.overlaps(window) }
    if (visible.isEmpty()) return
    withTransform({
        translate(origin.x, origin.y)
        scale(scale.x, scale.y, pivot = Offset.Zero)
    }) {
        for (layer in DetailLayer.entries) {
            if (layer == DetailLayer.LOCAL_ROAD && span > .12f) continue
            val colour = when (layer) {
                DetailLayer.PARK -> colours.park
                DetailLayer.WATER, DetailLayer.RIVER -> colours.water
                DetailLayer.LOCAL_ROAD -> colours.localRoad
                DetailLayer.MAIN_ROAD -> colours.mainRoad
            }
            val width = when (layer) {
                DetailLayer.RIVER -> if (span < .07f) 5.dp else 2.dp
                DetailLayer.MAIN_ROAD -> if (span < .07f) 2.5.dp else 1.45.dp
                else -> if (span < .045f) 1.2.dp else .65.dp
            }
            val stroke = Stroke(width.toPx() / scale.y, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (cell in visible) {
                val path = cell.layers[layer.ordinal]
                if (layer.closed) drawPath(path, colour) else drawPath(path, colour, style = stroke)
            }
        }
    }
}

private fun DrawScope.drawLabels(
    places: List<BasemapPlace>, viewport: MapViewport, scale: Offset, marker: Offset,
    paint: Paint, halo: Paint, rankLimit: Int,
) {
    val safeArea = Rect(12.dp.toPx(), 28.dp.toPx(),
        (size.width - 72.dp.toPx()).coerceAtLeast(12.dp.toPx()), (size.height - 122.dp.toPx()).coerceAtLeast(28.dp.toPx()))
    val occupied = mutableListOf<Rect>()
    val names = mutableSetOf<String>()
    for (place in places) {
        if (place.rank > rankLimit || occupied.size >= 18) break
        if (place.name in names) continue
        val point = viewport.screenPoint(place.point, center, scale)
        if (!safeArea.contains(point)) continue
        val x = point.x + 4.dp.toPx()
        val textBox = Rect(x, point.y + paint.ascent(), x + paint.measureText(place.name), point.y + paint.descent())
        if (!safeArea.contains(textBox.topRight) || !safeArea.contains(textBox.bottomLeft) ||
            textBox.inflate(16.dp.toPx()).contains(marker) || occupied.any { it.overlaps(textBox) }
        ) continue
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawText(place.name, x, point.y, halo)
        canvas.drawText(place.name, x, point.y, paint)
        occupied.add(textBox.inflate(5.dp.toPx()))
        names.add(place.name)
    }
}
