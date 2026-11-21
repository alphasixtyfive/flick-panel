package app.flickpanel.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CameraPlayer(
    stream: HttpUrl,
    transport: OkHttpClient,
    fillVideo: Boolean,
    modifier: Modifier,
    onAspectRatio: (Float) -> Unit,
    onFirstFrame: () -> Unit,
    onBuffering: (Boolean) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var player by remember(stream, transport) { mutableStateOf<ExoPlayer?>(null) }
    val reportAspectRatio by rememberUpdatedState(onAspectRatio)
    val reportFirstFrame by rememberUpdatedState(onFirstFrame)
    val reportBuffering by rememberUpdatedState(onBuffering)
    val reportError by rememberUpdatedState(onError)

    LifecycleStartEffect(stream, transport) {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 500, 1_500).build()
        val active = ExoPlayer.Builder(context, renderers).setLoadControl(loadControl).build()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = reportFirstFrame()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    reportAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }

            override fun onPlayerError(error: PlaybackException) = reportError()

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> Unit
                    Player.STATE_BUFFERING -> reportBuffering(true)
                    Player.STATE_READY -> reportBuffering(false)
                    Player.STATE_ENDED -> reportError()
                }
            }
        }
        val source = HlsMediaSource.Factory(OkHttpDataSource.Factory(transport))
        active.volume = 0f
        active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        active.setMediaSource(source.createMediaSource(MediaItem.fromUri(stream.toString())))
        active.addListener(listener)
        player = active
        active.prepare()
        active.playWhenReady = true

        onStopOrDispose {
            active.removeListener(listener)
            active.release()
            player = null
        }
    }
    val active = player ?: return
    AndroidView(
        factory = { PlayerView(it).apply {
            useController = false
            setShutterBackgroundColor(Color.TRANSPARENT)
        } },
        update = {
            it.resizeMode = if (fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            it.player = active
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
