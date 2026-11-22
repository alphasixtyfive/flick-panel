package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.camera.cameraSources
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun CameraStill(
    id: String, model: PanelViewModel, refresh: Boolean, modifier: Modifier,
    placeholder: String? = null, blurred: Boolean = false, allowStored: Boolean = true,
) {
    val cache = model.cameraPreviews
    var still by remember(cache, id, allowStored) {
        mutableStateOf<Bitmap?>(if (allowStored) cache.cached(id) else null)
    }
    var unavailable by remember(cache, id) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cache, id, refresh, blurred, allowStored, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (allowStored && still == null) still = cache.restore(id)
            if (!refresh || blurred && still != null) return@repeatOnLifecycle
            while (isActive) {
                try {
                    val fresh = model.refreshCameraPreview(id)
                    if (fresh != null) still = fresh
                    unavailable = fresh == null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    unavailable = true
                }
                if (blurred) break
                delay(30_000)
            }
        }
    }

    val bitmap = remember(still) { still?.asImageBitmap() }
    val background = if (placeholder == null) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val image = Modifier.matchParentSize()
            Image(bitmap, null, if (blurred) image.blur(7.dp, BlurredEdgeTreatment.Rectangle) else image,
                contentScale = ContentScale.Crop)
            if ((!refresh || unavailable) && placeholder != null) {
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = viewerSurface,
                    contentColor = Color.White, shape = CircleShape) {
                    Text("Last preview", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } else if (placeholder != null) {
            Text(if (refresh && unavailable) "Preview unavailable" else placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CameraTile(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, widescreen: Boolean = false) {
    val online = state.link == Link.ONLINE && state.entity(tile.entity)?.available != false
    val previewActive = online && state.camera == null
    val nativeCamera = cameraSources(tile.entity, tile, state.entities).nativeCamera
    var nativeTalk by remember(nativeCamera, online) { mutableStateOf(false) }
    LaunchedEffect(nativeCamera, previewActive) {
        if (previewActive) nativeTalk = model.cameraCapabilities(nativeCamera).twoWayAudio
    }
    val preview = if (widescreen) modifier.aspectRatio(16f / 9f) else modifier
    Surface(
        onClick = { model.openCamera(tile.entity) },
        enabled = online,
        modifier = preview.semantics { if (online) onClick(label = "Open live camera", action = null) },
        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(Modifier.fillMaxSize()) {
            CameraStill(tile.entity, model, previewActive, Modifier.matchParentSize(),
                placeholder = if (online) "Camera preview" else "Camera offline",
                allowStored = state.link != Link.AUTH_REJECTED)
            if (tile.intercomStream != null || nativeTalk) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp).background(viewerSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MicNone, "Intercom", Modifier.size(18.dp), tint = Color.White)
                }
            }
            TileTitle(state.label(tile), modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xdc101317))))
                .padding(start = tileContentPadding, top = 24.dp, end = tileContentPadding, bottom = 14.dp),
                color = Color.White)
        }
    }
}
