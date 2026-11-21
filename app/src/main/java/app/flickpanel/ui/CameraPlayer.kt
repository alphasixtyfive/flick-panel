package app.flickpanel.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.flickpanel.R
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("InflateParams") // AndroidView supplies the parent and layout parameters.
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
        val active = createCameraPlayer(context, stream, transport)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = reportFirstFrame()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    reportAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("FlickPanelCamera", error.errorCodeName)
                reportError()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> Unit
                    Player.STATE_BUFFERING -> reportBuffering(true)
                    Player.STATE_READY -> reportBuffering(false)
                    Player.STATE_ENDED -> reportError()
                }
            }
        }
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
        factory = { LayoutInflater.from(it).inflate(R.layout.camera_player, null) as PlayerView },
        update = {
            it.resizeMode = if (fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            it.player = active
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun createCameraPlayer(context: Context, stream: HttpUrl, transport: OkHttpClient): ExoPlayer {
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    if (Build.DEVICE.equals("TB128FU", ignoreCase = true)) {
        // This Lenovo decoder can stall on HEVC streams above its advertised level.
        renderers.forceDisableMediaCodecAsynchronousQueueing()
        renderers.setMediaCodecSelector { mimeType, secure, tunneling ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            if (mimeType == MimeTypes.VIDEO_H265) decoders.sortedBy { !it.softwareOnly } else decoders
        }
    }
    val buffer = DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 500, 1_500).build()
    val source = HlsMediaSource.Factory(OkHttpDataSource.Factory(transport))
        .createMediaSource(MediaItem.fromUri(stream.toString()))
    return ExoPlayer.Builder(context, renderers).setLoadControl(buffer).build().apply {
        volume = 0f
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        setMediaSource(source)
    }
}
