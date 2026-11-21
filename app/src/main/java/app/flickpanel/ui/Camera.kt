package app.flickpanel.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import kotlin.math.abs

@Composable
private fun CameraStill(
    id: String, model: PanelViewModel, active: Boolean, modifier: Modifier,
    placeholder: String? = null, blurred: Boolean = false
) {
    var still by remember(id, active) { mutableStateOf<Bitmap?>(if (active) model.cachedSnapshot(id) else null) }
    var unavailable by remember(id, active) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(id, active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    val fresh = model.snapshot(id)
                    if (fresh != null) still = fresh
                    unavailable = fresh == null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    unavailable = true
                }
                delay(25_000)
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
        } else if (placeholder != null) {
            Text(if (unavailable) "Preview unavailable" else placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CameraTile(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, widescreen: Boolean = false) {
    val online = state.link == Link.ONLINE && state.entity(tile.entity)?.available != false
    CameraCard(tile.entity, state.label(tile), online, online && state.camera == null, model, modifier, widescreen)
}

@Composable
private fun CameraCard(
    id: String, label: String, online: Boolean, previewActive: Boolean, model: PanelViewModel,
    modifier: Modifier, widescreen: Boolean,
) {
    val preview = if (widescreen) modifier.aspectRatio(16f / 9f) else modifier
    Surface(onClick = { model.openCamera(id) }, enabled = online,
        modifier = preview.semantics { if (online) onClick(label = "Open live camera", action = null) },
        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxSize()) {
            CameraStill(id, model, previewActive, Modifier.matchParentSize(),
                placeholder = if (online) "Camera preview" else "Camera offline")
            TileTitle(label, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xdc101317))))
                .padding(start = tileContentPadding, top = 24.dp, end = tileContentPadding, bottom = 14.dp),
                color = Color.White)
        }
    }
}

internal enum class CameraStatus(val label: String) {
    STILL("Still image"),
    CONNECTING("Connecting…"),
    LIVE("Live"),
    RECONNECTING("Reconnecting…"),
    UNAVAILABLE("Live stream unavailable");

    val buffering get() = this == CONNECTING || this == RECONNECTING
}

private data class CameraStream(val url: HttpUrl, val transport: OkHttpClient)

@Composable
internal fun CameraViewer(
    id: String, label: String?, canStream: Boolean, openedAt: Long, gateId: String?, state: PanelUi, model: PanelViewModel,
    streamCamera: String = id,
) {
    var stream by remember(streamCamera, canStream) { mutableStateOf<CameraStream?>(null) }
    var status by remember(streamCamera, canStream) {
        mutableStateOf(if (canStream) CameraStatus.CONNECTING else CameraStatus.STILL)
    }
    var videoAspect by remember(streamCamera, canStream) { mutableStateOf<Float?>(null) }
    var firstFrame by remember(streamCamera, canStream) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun playbackFailed() {
        stream = null
        firstFrame = false
        status = CameraStatus.UNAVAILABLE
    }

    LaunchedEffect(streamCamera, canStream, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            status = if (canStream) CameraStatus.CONNECTING else CameraStatus.STILL
            videoAspect = null
            firstFrame = false
            if (!canStream) return@repeatOnLifecycle
            try {
                val transport = model.videoTransport()
                stream = CameraStream(model.stream(streamCamera), transport)
                Log.i("FlickPanelCamera", "hls_url_ms=${SystemClock.elapsedRealtime() - openedAt}")
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                playbackFailed()
            } finally {
                stream = null
            }
        }
    }

    val activeStream = stream
    LaunchedEffect(activeStream, status, lifecycle) {
        if (activeStream != null && status.buffering) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(15_000)
                if (stream === activeStream && status.buffering) playbackFailed()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewportAspect = maxWidth.value / maxHeight.value
        val fillVideo = videoAspect?.let { abs(viewportAspect / it - 1f) < .10f } == true
        if (activeStream != null) {
            CameraPlayer(activeStream.url, activeStream.transport, fillVideo, Modifier.matchParentSize(),
                onAspectRatio = { videoAspect = it },
                onFirstFrame = {
                    if (!firstFrame) {
                        Log.i("FlickPanelCamera", "first_frame_ms=${SystemClock.elapsedRealtime() - openedAt} transport=hls")
                    }
                    firstFrame = true
                    status = CameraStatus.LIVE
                },
                onBuffering = { buffering ->
                    if (firstFrame) status = if (buffering) CameraStatus.RECONNECTING else CameraStatus.LIVE
                },
                onError = ::playbackFailed,
            )
        }
        if (!firstFrame) CameraStill(id, model, true, Modifier.matchParentSize(), blurred = status.buffering)
        val gate: (@Composable (Modifier) -> Unit)? = gateId?.let { gate ->
            { modifier -> GateControl(state, gate, { model.openCameraGate(id) }, modifier) }
        }
        CameraControls(label ?: "Camera", status, model::closeCamera, Modifier.matchParentSize(), gate)
    }
}

@Composable
internal fun CameraControls(
    label: String,
    status: CameraStatus,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    gate: (@Composable (Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.displayCutout)) {
        val compact = maxWidth < 600.dp || LocalDensity.current.fontScale >= 1.3f
        val inset = if (compact || maxHeight < 480.dp) 16.dp else 24.dp
        Column(Modifier.fillMaxSize().padding(inset)) {
            ViewerIconButton(Icons.Rounded.Close, "Close camera", close,
                Modifier.align(Alignment.End).size(56.dp))
            if (compact) gate?.invoke(Modifier.align(Alignment.End).padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
            ) {
                Box(Modifier.weight(1f)) {
                    CameraCaption(label, status)
                }
                if (!compact) gate?.invoke(Modifier)
            }
        }
    }
}

@Composable
private fun CameraCaption(label: String, status: CameraStatus) {
    Surface(
        Modifier.widthIn(max = 420.dp),
        color = Color(0xdd171a1d),
        contentColor = Color.White,
        shape = panelCardShape,
    ) {
        Column(
            Modifier.padding(horizontal = 19.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == CameraStatus.LIVE) Box(Modifier.size(8.dp).background(Color(0xff7fe6a8), CircleShape))
                Text(
                    status.label,
                    color = Color(0xffe1e7ed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
