package app.flickpanel.ui

import android.os.SystemClock
import android.util.Log

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.HttpUrl
import kotlin.math.abs

@Composable
private fun CameraStill(
    id: String, model: PanelViewModel, active: Boolean, modifier: Modifier,
    placeholder: String? = null, blurred: Boolean = false
) {
    var still by remember(id, active) { mutableStateOf<Bitmap?>(if (active) model.cachedSnapshot(id) else null) }
    var unavailable by remember(id, active) { mutableStateOf(false) }
    LaunchedEffect(id, active) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            try {
                val fresh = model.snapshot(id)
                if (fresh != null) still = fresh
                unavailable = fresh == null
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable = true }
            delay(25_000)
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
    val preview = if (widescreen) modifier.aspectRatio(16f / 9f) else modifier
    val online = state.link == Link.ONLINE && state.entity(tile.entity)?.available != false
    Surface(onClick = { model.openCamera(tile.entity) }, enabled = online, modifier = preview, shape = panelCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxSize()) {
            CameraStill(tile.entity, model, online && state.camera == null, Modifier.matchParentSize(),
                placeholder = if (online) "Camera preview" else "Camera offline")
            if (!widescreen) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(12.dp)
                .background(Brush.verticalGradient(listOf(Color(0xff101317), Color.Transparent))))
            Text(state.label(tile), modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xdc101317))))
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 14.dp),
                color = Color.White, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(38.dp)
                .background(Color(0xc0171a1d), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowOutward, "Open live camera", Modifier.size(23.dp), tint = Color.White)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CameraViewer(
    id: String, label: String?, canStream: Boolean, openedAt: Long, gateId: String?, state: PanelUi, model: PanelViewModel,
) {
    var url by remember(id) { mutableStateOf<HttpUrl?>(null) }
    var status by remember(id) { mutableStateOf(if (canStream) "Connecting…" else "Still image") }
    var firstFrame by remember(id) { mutableStateOf(false) }
    var videoAspect by remember(id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(id, canStream) {
        if (!canStream) {
            url = null
            status = "Still image"
            return@LaunchedEffect
        }
        try {
            url = model.stream(id)
            Log.i("FlickPanelCamera", "hls_url_ms=${SystemClock.elapsedRealtime() - openedAt}")
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = "Live stream unavailable" }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewportAspect = maxWidth.value / maxHeight.value
        val fillVideo = videoAspect?.let { abs(viewportAspect / it - 1f) < .10f } == true
        url?.let { stream ->
            val context = LocalContext.current
            val player = remember(stream) {
                val applicationContext = context.applicationContext
                val renderers = DefaultRenderersFactory(applicationContext).setEnableDecoderFallback(true)
                val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 500, 1_500).build()
                ExoPlayer.Builder(applicationContext, renderers).setLoadControl(loadControl).build().apply {
                    volume = 0f
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                    val source = HlsMediaSource.Factory(OkHttpDataSource.Factory(model.videoTransport()))
                    setMediaSource(source.createMediaSource(MediaItem.fromUri(stream.toString())))
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        if (!firstFrame) Log.i("FlickPanelCamera", "first_frame_ms=${SystemClock.elapsedRealtime() - openedAt} transport=hls")
                        firstFrame = true
                        status = "Live"
                    }
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        firstFrame = false
                        status = "Live stream unavailable"
                        url = null
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING && firstFrame) status = "Connecting…"
                        else if (playbackState == Player.STATE_READY && firstFrame) status = "Live"
                    }
                }
                player.addListener(listener)
                player.prepare()
                player.playWhenReady = true
                onDispose { player.removeListener(listener); player.release() }
            }
            LaunchedEffect(player, status) {
                if (status == "Connecting…") {
                    delay(15_000)
                    firstFrame = false
                    status = "Live stream unavailable"
                    url = null
                }
            }
            AndroidView(factory = { PlayerView(it).apply {
                useController = false
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
            } }, update = {
                it.resizeMode = if (fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                it.player = player
            }, onRelease = { it.player = null },
                modifier = Modifier.matchParentSize())
        }
        if (!firstFrame) CameraStill(id, model, true, Modifier.matchParentSize(), blurred = status == "Connecting…")
        FilledIconButton(onClick = model::closeCamera,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(56.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xdd1b1e21), contentColor = Color.White)) {
            Icon(Icons.Rounded.Close, "Close camera")
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 420.dp),
            color = Color(0xdd171a1d), contentColor = Color.White, shape = panelCardShape) {
            Column(Modifier.padding(horizontal = 19.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label ?: "Camera", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status == "Live") Box(Modifier.size(8.dp).background(Color(0xff7fe6a8), CircleShape))
                    Text(status, color = Color(0xffe1e7ed), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (gateId != null) {
            val gatePosition = if (maxWidth < 600.dp) {
                Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 24.dp)
            } else {
                Modifier.align(Alignment.BottomEnd).padding(24.dp)
            }
            GateControl(state, gateId, { model.openCameraGate(id) }, gatePosition)
        }
    }
}
