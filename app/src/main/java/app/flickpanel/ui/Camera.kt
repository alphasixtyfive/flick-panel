package app.flickpanel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.camera.cameraRoutes
import app.flickpanel.camera.cameraSources
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.CameraCapabilities
import app.flickpanel.ha.CameraStream
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal enum class CameraStatus(val label: String) {
    STILL("Still image"),
    CONNECTING("Connecting…"),
    LIVE("Live"),
    RECONNECTING("Reconnecting…"),
    UNAVAILABLE("Live stream unavailable");

    val buffering get() = this == CONNECTING || this == RECONNECTING
    val hasVideo get() = this == LIVE || this == RECONNECTING
}

private enum class IntercomPlayback { CONNECTING, RETRYING, LIVE, UNAVAILABLE }

private const val intercomFallbackDelayMs = 650L

@Composable
internal fun CameraViewer(
    id: String, label: String?, openedAt: Long, tile: TileSpec?, state: PanelUi, model: PanelViewModel,
) {
    val online = state.link == Link.ONLINE
    val (nativeCamera, streamCamera, stream) = cameraSources(id, tile, state.entities)
    val features = state.entity(streamCamera)?.attributes?.optLong("supported_features", -1L) ?: -1L
    val canStream = features and 2L != 0L
    val initialHlsStatus = when {
        !online -> CameraStatus.UNAVAILABLE
        !canStream -> CameraStatus.STILL
        else -> CameraStatus.CONNECTING
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val hasMic = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }
    var capabilities by remember(nativeCamera, online) { mutableStateOf<CameraCapabilities?>(null) }
    var talking by remember(stream, online) { mutableStateOf(false) }
    var micOn by remember(stream, online) { mutableStateOf(false) }
    var microphoneRequestedAt by remember(stream, online) { mutableLongStateOf(0L) }
    var muted by remember(id, openedAt) { mutableStateOf(false) }
    var intercom by remember(stream, online) { mutableStateOf(IntercomPlayback.CONNECTING) }
    var attempt by remember(stream, online) { mutableIntStateOf(0) }
    var canTalk by remember(stream, online) { mutableStateOf(false) }
    var intercomHasAudio by remember(stream, online) { mutableStateOf(false) }
    var notice by remember(stream, online) { mutableStateOf<String?>(null) }
    var hlsPreview by remember(stream, online) { mutableStateOf(false) }
    var hlsStatus by remember(streamCamera, initialHlsStatus) { mutableStateOf(initialHlsStatus) }
    var hlsHasAudio by remember(streamCamera, initialHlsStatus) { mutableStateOf(false) }
    var loggedFirstFrame by remember(openedAt) { mutableStateOf(false) }
    val routes = remember(capabilities, stream) { cameraRoutes(capabilities ?: CameraCapabilities.None, stream != null) }
    val findingRoute = online && capabilities == null
    val useIntercom = !findingRoute && online && routes.isNotEmpty() && intercom != IntercomPlayback.UNAVAILABLE
    val intercomLive = useIntercom && intercom == IntercomPlayback.LIVE
    val showHls = (!findingRoute && !useIntercom) ||
        (canStream && !intercomLive && (stream == null || hlsPreview))
    val hasAudio = if (intercomLive) intercomHasAudio else showHls && hlsHasAudio
    val audioRequested = talking || (!muted && (hasAudio || (useIntercom && intercomHasAudio)))
    val audioFocused = rememberCameraAudioFocus(audioRequested) {
        notice = if (talking) "Call disconnected" else "Camera audio paused"
        muted = true
        talking = false
    }

    LaunchedEffect(nativeCamera, online) {
        if (online) {
            capabilities = withTimeoutOrNull(900) { model.cameraCapabilities(nativeCamera) } ?: CameraCapabilities.None
        }
    }
    val tryVideoFallback = stream != null && canStream && (findingRoute || useIntercom) && !intercomLive
    LaunchedEffect(tryVideoFallback) {
        if (tryVideoFallback) {
            delay(intercomFallbackDelayMs)
            hlsPreview = true
        }
    }

    fun setTalkMode(enabled: Boolean) {
        notice = null
        talking = enabled
        if (enabled) microphoneRequestedAt = SystemClock.elapsedRealtime()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && useIntercom) setTalkMode(true)
        else if (!granted) notice = "Microphone permission needed"
    }
    DisposableEffect(lifecycle, stream, online) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (intercom != IntercomPlayback.UNAVAILABLE) intercom = IntercomPlayback.CONNECTING
                talking = false
                micOn = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun logFrame(transport: String) {
        if (!loggedFirstFrame) {
            Log.i("FlickPanelCamera", "first_frame_ms=${SystemClock.elapsedRealtime() - openedAt} transport=$transport")
            loggedFirstFrame = true
        }
    }

    fun toggleTalk() {
        when {
            talking -> setTalkMode(false)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                permission.launch(Manifest.permission.RECORD_AUDIO)
            else -> setTalkMode(true)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (useIntercom) {
            CameraIntercom(
                id = nativeCamera,
                stream = stream,
                routes = routes,
                attempt = attempt,
                audioEnabled = audioFocused && intercomLive && (!muted || talking),
                talk = audioFocused && intercomLive && talking && canTalk,
                model = model,
                modifier = Modifier.matchParentSize(),
                connecting = { intercom = IntercomPlayback.CONNECTING },
                retrying = {
                    intercom = IntercomPlayback.RETRYING
                    hlsPreview = true
                    if (talking) notice = "Call disconnected"
                    talking = false
                    micOn = false
                },
                live = { hasStreamAudio, talkSupported, transport ->
                    canTalk = talkSupported
                    intercomHasAudio = hasStreamAudio
                    intercom = IntercomPlayback.LIVE
                    hlsPreview = false
                    hlsStatus = initialHlsStatus
                    hlsHasAudio = false
                    logFrame(transport)
                },
                microphone = { active ->
                    micOn = active
                    if (active && microphoneRequestedAt != 0L) {
                        Log.i("FlickPanelCamera", "talk_ready_ms=${SystemClock.elapsedRealtime() - microphoneRequestedAt}")
                    }
                },
                microphoneError = {
                    talking = false
                    micOn = false
                    notice = "Microphone unavailable"
                },
                failed = {
                    talking = false
                    micOn = false
                    intercomHasAudio = false
                    intercom = IntercomPlayback.UNAVAILABLE
                    if (routes.any { it.talk }) notice = "Intercom unavailable"
                },
            )
        }

        if (showHls) {
            HlsCamera(
                streamCamera, openedAt, initialHlsStatus, maxWidth.value / maxHeight.value, model,
                Modifier.matchParentSize(),
                audioEnabled = audioFocused && !muted && hlsHasAudio,
                onAudioAvailable = { hlsHasAudio = it },
                onStatus = { hlsStatus = it },
                onLive = {
                    hlsStatus = CameraStatus.LIVE
                    logFrame("hls")
                },
            )
        }
        val status = when {
            intercomLive -> CameraStatus.LIVE
            showHls -> hlsStatus
            intercom == IntercomPlayback.RETRYING -> CameraStatus.RECONNECTING
            else -> CameraStatus.CONNECTING
        }
        if (!intercomLive && !(showHls && hlsStatus.hasVideo)) {
            CameraStill(id, model, online, Modifier.matchParentSize(), blurred = status.buffering,
                allowStored = state.link != Link.AUTH_REJECTED)
        }
        val expectsTalk = routes.any { it.talk }
        val retry = online && expectsTalk && (intercom == IntercomPlayback.UNAVAILABLE || intercomLive && !canTalk)
        val talkControl: (@Composable (Boolean) -> Unit)? = if (online && expectsTalk && (hasMic || retry)) {
            { compact ->
                IntercomTalkButton(talking, intercomLive && canTalk, retry, compact) {
                    if (retry) {
                        notice = null
                        hlsPreview = true
                        intercom = IntercomPlayback.CONNECTING
                        attempt++
                    } else toggleTalk()
                }
            }
        } else null
        val speakerControl: (@Composable (Modifier) -> Unit)? = if (hasAudio && !talking) {
            { controlModifier ->
                ViewerIconButton(
                    icon = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = if (muted) "Unmute camera" else "Mute camera",
                    onClick = {
                        notice = null
                        muted = !muted
                    },
                    modifier = controlModifier,
                )
            }
        } else null
        CameraControls(
            label ?: "Camera", status,
            startingCall = intercomLive && talking && !micOn,
            micOn = intercomLive && micOn,
            notice = notice ?: when {
                useIntercom && expectsTalk && !intercomLive && showHls && hlsStatus.hasVideo ->
                    if (intercom == IntercomPlayback.RETRYING) "Reconnecting audio…" else "Connecting audio…"
                else -> null
            },
            close = model::closeCamera,
            state = state,
            actions = tile?.cameraActions.orEmpty(),
            runAction = { entity -> model.runCameraAction(id, entity) },
            talkControl = talkControl,
            speakerControl = speakerControl,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun HlsCamera(
    streamCamera: String, openedAt: Long, initialStatus: CameraStatus, viewportAspect: Float,
    model: PanelViewModel, modifier: Modifier, audioEnabled: Boolean, onAudioAvailable: (Boolean) -> Unit,
    onStatus: (CameraStatus) -> Unit, onLive: () -> Unit,
) {
    var stream by remember(streamCamera, initialStatus) { mutableStateOf<CameraStream?>(null) }
    var buffering by remember(streamCamera, initialStatus) { mutableStateOf(true) }
    var rendered by remember(streamCamera, initialStatus) { mutableStateOf(false) }
    var videoAspect by remember(streamCamera, initialStatus) { mutableStateOf<Float?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun playbackFailed() {
        stream = null
        onAudioAvailable(false)
        onStatus(CameraStatus.UNAVAILABLE)
    }

    LaunchedEffect(streamCamera, initialStatus, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            onStatus(initialStatus)
            buffering = true
            rendered = false
            videoAspect = null
            if (initialStatus != CameraStatus.CONNECTING) return@repeatOnLifecycle
            try {
                stream = model.cameraStream(streamCamera)
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

    val active = stream ?: return
    LaunchedEffect(active, buffering) {
        if (buffering) {
            delay(15_000)
            if (stream === active && buffering) playbackFailed()
        }
    }
    val fill = videoAspect?.let { abs(viewportAspect / it - 1f) < .10f } == true
    CameraPlayer(
        active.url, active.transport, fill, audioEnabled, modifier,
        onAspectRatio = { videoAspect = it },
        onFirstFrame = {
            rendered = true
            buffering = false
            onLive()
        },
        onBuffering = {
            buffering = it
            if (rendered) onStatus(if (it) CameraStatus.RECONNECTING else CameraStatus.LIVE)
        },
        onAudioAvailable = onAudioAvailable,
        onError = ::playbackFailed,
    )
}
