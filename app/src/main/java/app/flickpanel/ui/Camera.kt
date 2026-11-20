package app.flickpanel.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.HttpUrl

@Composable
private fun CameraStill(id: String, model: PanelViewModel, active: Boolean, modifier: Modifier, fit: Boolean = false) {
    var still by remember(id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, active) {
        still = null
        if (!active) return@LaunchedEffect
        while (isActive) {
            try {
                val fresh = model.snapshot(id)
                if (fresh != null) still = fresh
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            delay(25_000)
        }
    }
    val bitmap = remember(still) { still?.asImageBitmap() }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
        else Text(if (active) "Camera preview" else "Camera offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CameraTile(tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier) {
    Surface(onClick = { model.openCamera(tile.entity) }, modifier = modifier, shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxSize()) {
            CameraStill(tile.entity, model, state.link == Link.ONLINE && state.camera == null,
                Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.label(tile), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ArrowOutward, "Open live camera", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CameraViewer(id: String, label: String?, model: PanelViewModel) {
    var url by remember(id) { mutableStateOf<HttpUrl?>(null) }
    var status by remember(id) { mutableStateOf("Connecting…") }
    var firstFrame by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        try { url = model.stream(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = "Live stream unavailable" }
    }
    Dialog(onDismissRequest = model::closeCamera, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(18.dp).widthIn(max = 1030.dp).fillMaxWidth().fillMaxHeight(.89f),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 19.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label ?: "Camera", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = model::closeCamera) { Icon(Icons.Rounded.Close, "Close camera") }
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                    if (!firstFrame) CameraStill(id, model, true, Modifier.fillMaxSize(), fit = true)
                    url?.let { stream ->
                        val context = LocalContext.current
                        val player = remember(stream) {
                            ExoPlayer.Builder(context.applicationContext).build().apply {
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
                                    firstFrame = true
                                    status = "Live"
                                }
                                override fun onPlayerError(error: PlaybackException) {
                                    firstFrame = false
                                    status = "Live stream unavailable"
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
                        LaunchedEffect(player) {
                            delay(16_000)
                            if (!firstFrame) {
                                status = "Live stream unavailable"
                                url = null
                            }
                        }
                        AndroidView(factory = { PlayerView(it).apply {
                            useController = false
                            setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                        } }, update = { it.player = player }, onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
