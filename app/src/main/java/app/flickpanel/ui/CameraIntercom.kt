package app.flickpanel.ui

import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.PanelViewModel
import app.flickpanel.camera.CameraAudio
import app.flickpanel.camera.CameraRoute
import app.flickpanel.camera.CameraSignaling
import app.flickpanel.camera.Go2RtcSocket
import app.flickpanel.camera.IntercomConnectionProblem
import app.flickpanel.camera.connectIntercom
import app.flickpanel.camera.playIntercom
import app.flickpanel.ha.HaProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.SurfaceViewRenderer

private const val webRtcStartupWindowMillis = 8_000L
private const val webRtcTalkFallbackWindowMillis = 11_000L
private class CameraStartupExpired : Exception()

@Composable
internal fun CameraIntercom(
    id: String,
    stream: String?,
    routes: List<CameraRoute>,
    attempt: Int,
    audioEnabled: Boolean,
    talk: Boolean,
    fitPortrait: Boolean,
    model: PanelViewModel,
    modifier: Modifier,
    connecting: () -> Unit,
    retrying: () -> Unit,
    live: (hasAudio: Boolean, canTalk: Boolean, transport: String) -> Unit,
    microphone: (active: Boolean) -> Unit,
    microphoneError: () -> Unit,
    failed: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audio = remember(id) { MutableStateFlow(CameraAudio()) }
    val onConnecting by rememberUpdatedState(connecting)
    val onRetrying by rememberUpdatedState(retrying)
    val onLive by rememberUpdatedState(live)
    val onMicrophone by rememberUpdatedState(microphone)
    val onMicrophoneError by rememberUpdatedState(microphoneError)
    val onFailure by rememberUpdatedState(failed)
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    SideEffect { audio.value = CameraAudio(audioEnabled, talk) }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).also { view ->
                    // Flexible bounds let WebRTC fit the video to its native aspect ratio.
                    view.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    renderer = view
                }
            },
            onRelease = { renderer = null },
            modifier = if (fitPortrait) Modifier else Modifier.fillMaxSize(),
        )
    }

    val surface = renderer ?: return
    LaunchedEffect(lifecycle, surface, id, stream, routes, attempt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            onConnecting()
            val hasTalkFallback = CameraRoute.HOME_ASSISTANT_TALK in routes && CameraRoute.GO2RTC in routes
            val startupWindow = if (hasTalkFallback) webRtcTalkFallbackWindowMillis else webRtcStartupWindowMillis

            try {
                coroutineScope {
                    fun startFallbackTimer() = launch {
                        delay(startupWindow)
                        throw CameraStartupExpired()
                    }
                    var fallbackTimer = startFallbackTimer()

                    fun reconnecting() {
                        if (!fallbackTimer.isActive) fallbackTimer = startFallbackTimer()
                        audio.value = CameraAudio()
                        onRetrying()
                    }

                    suspend fun play(route: CameraRoute, signaling: CameraSignaling, startedAt: Long) {
                        val routeTimeout = if (hasTalkFallback && route == CameraRoute.HOME_ASSISTANT_TALK) {
                            3_500L
                        } else 5_500L
                        playIntercom(
                            context, surface, signaling, audio, routeTimeout,
                            onLive = { hasAudio, canTalk ->
                                if (route.talk && !canTalk) {
                                    throw IntercomConnectionProblem("Camera didn't negotiate talkback", retryable = false)
                                }
                                fallbackTimer.cancel()
                                val elapsed = SystemClock.elapsedRealtime() - startedAt
                                Log.i("FlickPanelCamera", "webrtc_ready_ms=$elapsed transport=${signaling.name} talkback=$canTalk")
                                onLive(hasAudio, canTalk, signaling.name)
                            },
                            onMicrophone = { active -> onMicrophone(active) },
                            onMicrophoneError = { onMicrophoneError() },
                        )
                    }

                    try {
                        for ((index, route) in routes.withIndex()) {
                            if (index != 0) reconnecting()
                            try {
                                connectIntercom(allowRetry = route == CameraRoute.GO2RTC, onRetry = { problem ->
                                    Log.w("FlickPanelCamera", "${problem.message}. Retrying go2rtc.")
                                    reconnecting()
                                }) {
                                    val startedAt = SystemClock.elapsedRealtime()
                                    if (route == CameraRoute.GO2RTC) {
                                        val address = withTimeoutOrNull(1_500) { model.cameraIntercom(checkNotNull(stream)) }
                                            ?: throw IntercomConnectionProblem("Home Assistant didn't configure the intercom in time")
                                        Go2RtcSocket(address).use { play(route, it, startedAt) }
                                    } else {
                                        val signaling = withTimeoutOrNull(1_500) { model.nativeCamera(id, route.talk) }
                                            ?: throw IntercomConnectionProblem("Home Assistant didn't configure WebRTC in time")
                                        play(route, signaling, startedAt)
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (problem: IntercomConnectionProblem) {
                                Log.w("FlickPanelCamera", problem.message.orEmpty())
                            } catch (problem: HaProblem) {
                                Log.w("FlickPanelCamera", problem.message.orEmpty())
                            } catch (problem: Exception) {
                                Log.w("FlickPanelCamera", "WebRTC playback failed (${problem.javaClass.simpleName})")
                            } catch (_: LinkageError) {
                                break
                            }
                            if (audio.value.talk) onMicrophoneError()
                            onMicrophone(false)
                        }
                    } finally {
                        fallbackTimer.cancel()
                    }
                }
            } catch (_: CameraStartupExpired) {
                Log.i("FlickPanelCamera", "WebRTC startup window elapsed")
            }
            onFailure()
        }
    }
}
