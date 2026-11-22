package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun VacuumMap(
    id: String, state: String?, available: Boolean, model: PanelViewModel, modifier: Modifier,
) {
    var retry by remember(model, id) { mutableIntStateOf(0) }
    val (bitmap, failed) = rememberPanelSnapshot(id, state, available, model, retry)
    VacuumMapSurface(bitmap, stale = !available || failed, modifier, retry = { retry += 1 }.takeIf { available && failed })
}

@Composable
internal fun VacuumMapSurface(bitmap: Bitmap?, stale: Boolean, modifier: Modifier, retry: (() -> Unit)? = null) {
    var frame by remember { mutableStateOf<VacuumMapFrame?>(null) }
    LaunchedEffect(bitmap) {
        frame = bitmap?.let { source ->
            withContext(Dispatchers.Default) { VacuumMapFrame(source.asImageBitmap(), source.mapContentBounds()) }
        }
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        val map = frame
        if (bitmap == null || map == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (stale) "Map unavailable" else "Loading map…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (retry != null) TextButton(onClick = retry) { Text("Retry map") }
                }
            }
        } else {
            ZoomableVacuumMap(map, stale, retry)
        }
    }
}

private data class VacuumMapFrame(val image: ImageBitmap, val bounds: IntRect)

@Composable
private fun ZoomableVacuumMap(map: VacuumMapFrame, stale: Boolean, retry: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val preferredPadding = if (maxWidth >= 600.dp) 44.dp else 18.dp
        val inset = with(LocalDensity.current) { minOf(preferredPadding.toPx(), viewport.minDimension * .2f) }
        val fit = minOf((viewport.width - inset * 2) / map.bounds.width, (viewport.height - inset * 2) / map.bounds.height)
        val fitted = Size(map.bounds.width * fit, map.bounds.height * fit)
        var zoom by remember(viewport, map.image.width, map.image.height) { mutableFloatStateOf(1f) }
        var offset by remember(viewport, map.image.width, map.image.height) { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { scale, pan, _ ->
            val nextZoom = (zoom * scale).coerceIn(1f, 5f)
            offset = (offset * (nextZoom / zoom) + pan).clampToMap(viewport, fitted, nextZoom)
            zoom = nextZoom
        }
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = "Vacuum map" }
                .transformable(transform)
                .graphicsLayer {
                    val translation = offset.clampToMap(viewport, fitted, zoom)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = translation.x
                    translationY = translation.y
                },
        ) {
            if (fitted.width < 1f || fitted.height < 1f) return@Canvas
            drawImage(
                map.image,
                srcOffset = IntOffset(map.bounds.left, map.bounds.top),
                srcSize = IntSize(map.bounds.width, map.bounds.height),
                dstOffset = IntOffset(((size.width - fitted.width) / 2).roundToInt(), ((size.height - fitted.height) / 2).roundToInt()),
                dstSize = IntSize(fitted.width.roundToInt(), fitted.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
        if (stale) {
            Surface(Modifier.align(Alignment.TopStart).padding(16.dp), shape = MaterialTheme.shapes.large) {
                if (retry != null) TextButton(onClick = retry) { Text("Retry map") }
                else Text("Last map", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        FilledTonalIconButton(onClick = {
            zoom = 1f
            offset = Offset.Zero
        }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp)) {
            Icon(Icons.Rounded.FitScreen, "Fit map")
        }
    }
}

private fun Offset.clampToMap(viewport: Size, fitted: Size, zoom: Float): Offset {
    val limitX = ((fitted.width * zoom - viewport.width) / 2).coerceAtLeast(0f)
    val limitY = ((fitted.height * zoom - viewport.height) / 2).coerceAtLeast(0f)
    return Offset(x.coerceIn(-limitX, limitX), y.coerceIn(-limitY, limitY))
}

private fun Bitmap.mapContentBounds(): IntRect {
    val full = IntRect(0, 0, width, height)
    if (!hasAlpha() || config == Bitmap.Config.HARDWARE) return full

    // Read one row at a time to fit transparent margins without copying the bitmap.
    val row = IntArray(width)
    var left = width
    var right = 0
    var top = height
    var bottom = 0
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        val first = row.indexOfFirst { it ushr 24 != 0 }
        if (first < 0) continue
        left = minOf(left, first)
        right = maxOf(right, row.indexOfLast { it ushr 24 != 0 } + 1)
        top = minOf(top, y)
        bottom = y + 1
    }
    return if (left < right && top < bottom) IntRect(left, top, right, bottom) else full
}
