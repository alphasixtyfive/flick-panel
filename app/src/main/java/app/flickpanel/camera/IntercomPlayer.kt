package app.flickpanel.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.flickpanel.ha.HaProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack.MediaType
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver.RtpTransceiverDirection
import org.webrtc.RtpTransceiver.RtpTransceiverInit
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CameraAudio(val listen: Boolean = false, val talk: Boolean = false)

internal suspend fun playIntercom(
    context: Context,
    renderer: SurfaceViewRenderer,
    signaling: CameraSignaling,
    audio: StateFlow<CameraAudio>,
    startupTimeoutMillis: Long,
    onLive: (hasAudio: Boolean, canTalk: Boolean) -> Unit,
    onMicrophone: (active: Boolean) -> Unit,
    onMicrophoneError: () -> Unit,
): Nothing = coroutineScope {
    WebRtcRuntime.initialize(context)
    val egl = EglBase.create()
    val rendered = CompletableDeferred<Unit>()
    val failure = CompletableDeferred<Exception>()
    val connected = CompletableDeferred<Unit>()
    val peerState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val candidates = Channel<IceCandidate>(64)
    val audioErrors = Channel<Unit>(Channel.CONFLATED)
    var factory: PeerConnectionFactory? = null
    var peer: PeerConnection? = null
    var microphone: CameraMicrophone? = null
    var video: VideoTrack? = null
    var channel: DataChannel? = null
    try {
        renderer.init(egl.eglBaseContext, object : RendererCommon.RendererEvents {
            override fun onFirstFrameRendered() { rendered.complete(Unit) }
            override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) = Unit
        })
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        val rtc = createFactory(context, egl, audioErrors)
        factory = rtc
        val configuration = PeerConnection.RTCConfiguration(signaling.iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            if (signaling.relayOnly) iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        val connection = rtc.createPeerConnection(configuration, CameraPeerObserver(peerState, connected, failure, candidates))
            ?: throw HaProblem("Couldn't start the camera player")
        peer = connection
        connection.setAudioRecording(false)
        connection.setAudioPlayout(false)
        channel = signaling.dataChannel?.let { connection.createDataChannel(it, DataChannel.Init()) }
        connection.addTransceiver(MediaType.MEDIA_TYPE_VIDEO, receiveOnly)
        val speaker = connection.addTransceiver(
            MediaType.MEDIA_TYPE_AUDIO,
            if (signaling.audio == CameraAudioOffer.TWO_WAY) sendReceive else receiveOnly,
        )
        val outgoing = when (signaling.audio) {
            CameraAudioOffer.LISTEN -> null
            CameraAudioOffer.TWO_WAY -> speaker
            CameraAudioOffer.SEPARATE_TALK -> connection.addTransceiver(MediaType.MEDIA_TYPE_AUDIO, sendOnly)
        }

        coroutineScope {
            launch { throw failure.await() }
            launch {
                peerState.collectLatest { state ->
                    when (state) {
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            delay(1_500)
                            failure.complete(IntercomConnectionProblem("Camera media connection lost"))
                        }
                        PeerConnection.PeerConnectionState.FAILED ->
                            failure.complete(IntercomConnectionProblem("Camera media connection failed"))
                        else -> Unit
                    }
                }
            }
            val answerReady = CompletableDeferred<Unit>()
            var phase = "local offer"
            val session = this
            withTimeoutOrNull(startupTimeoutMillis) {
                val offer = connection.createLocalOffer()
                session.launch {
                    val pending = mutableListOf<IceCandidate>()
                    signaling.exchange(offer.description, candidates).collect { message ->
                        when (message) {
                            is CameraSignal.Answer -> if (!answerReady.isCompleted) {
                                connection.applyAnswer(message.sdp)
                                video = connection.receivers.firstNotNullOfOrNull { it.track() as? VideoTrack }
                                video?.addSink(renderer)
                                pending.forEach(connection::addIceCandidate)
                                pending.clear()
                                answerReady.complete(Unit)
                            }
                            is CameraSignal.Candidate -> if (answerReady.isCompleted) {
                                connection.addIceCandidate(message.candidate)
                            } else {
                                if (pending.size >= 64) throw HaProblem("Invalid WebRTC response")
                                pending.add(message.candidate)
                            }
                        }
                    }
                    throw IntercomConnectionProblem("Camera signaling closed")
                }
                phase = "stream answer"
                answerReady.await()
                phase = "media connection"
                connected.await()
                phase = "video frame"
                rendered.await()
                true
            } ?: throw IntercomConnectionProblem("Camera timed out waiting for $phase")

            val hasAudio = speaker?.currentDirection in receiveDirections
            val canTalk = outgoing?.currentDirection in sendDirections
            if (outgoing != null && canTalk) microphone = CameraMicrophone(connection, rtc, outgoing.sender)
            onLive(hasAudio, canTalk)
            launch {
                for (ignored in audioErrors) {
                    if (audio.value.talk) {
                        microphone?.stop()
                        onMicrophoneError()
                    }
                }
            }
            audio.collect { state ->
                connection.setAudioPlayout(state.listen && hasAudio)
                val mic = microphone ?: return@collect
                if (state.talk && !mic.active) {
                    val start = SystemClock.elapsedRealtime()
                    if (mic.start()) {
                        Log.i("FlickPanelCamera", "microphone_attach_ms=${SystemClock.elapsedRealtime() - start}")
                        onMicrophone(true)
                    } else onMicrophoneError()
                } else if (!state.talk && mic.active) {
                    mic.stop()
                    onMicrophone(false)
                }
            }
        }
    } finally {
        microphone?.stop()
        peer?.setAudioPlayout(false)
        video?.removeSink(renderer)
        channel?.dispose()
        peer?.dispose()
        factory?.dispose()
        renderer.release()
        egl.release()
        candidates.cancel()
        audioErrors.cancel()
    }
}

private class CameraMicrophone(
    private val peer: PeerConnection, private val factory: PeerConnectionFactory, private val sender: RtpSender,
) {
    private var source: AudioSource? = null
    private var track: AudioTrack? = null
    val active get() = track != null

    fun start(): Boolean {
        val capture = factory.createAudioSource(MediaConstraints())
        val microphone = factory.createAudioTrack("camera-microphone", capture)
        microphone.setEnabled(false)
        if (!sender.setTrack(microphone, false)) {
            microphone.dispose()
            capture.dispose()
            return false
        }
        source = capture
        track = microphone
        microphone.setEnabled(true)
        peer.setAudioRecording(true)
        return true
    }

    fun stop() {
        val microphone = track ?: return
        peer.setAudioRecording(false)
        microphone.setEnabled(false)
        sender.setTrack(null, false)
        microphone.dispose()
        source?.dispose()
        track = null
        source = null
    }
}

private fun createFactory(context: Context, egl: EglBase, errors: Channel<Unit>): PeerConnectionFactory {
    val audio = JavaAudioDeviceModule.builder(context)
        .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) { errors.trySend(Unit) }

            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String,
            ) { errors.trySend(Unit) }

            override fun onWebRtcAudioRecordError(errorMessage: String) { errors.trySend(Unit) }
        }).createAudioDeviceModule()
    return try {
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(audio)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    } finally {
        audio.release()
    }
}

private val receiveOnly = RtpTransceiverInit(RtpTransceiverDirection.RECV_ONLY)
private val sendOnly = RtpTransceiverInit(RtpTransceiverDirection.SEND_ONLY)
private val sendReceive = RtpTransceiverInit(RtpTransceiverDirection.SEND_RECV)
private val sendDirections = setOf(RtpTransceiverDirection.SEND_ONLY, RtpTransceiverDirection.SEND_RECV)
private val receiveDirections = setOf(RtpTransceiverDirection.RECV_ONLY, RtpTransceiverDirection.SEND_RECV)

private object WebRtcRuntime {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        initialized = true
    }
}

private class CameraPeerObserver(
    private val peerState: MutableStateFlow<PeerConnection.PeerConnectionState>,
    private val connected: CompletableDeferred<Unit>,
    private val failure: CompletableDeferred<Exception>,
    private val candidates: Channel<IceCandidate>,
) : PeerConnection.Observer {
    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
        peerState.value = state
        if (state == PeerConnection.PeerConnectionState.CONNECTED) connected.complete(Unit)
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        if (!candidates.trySend(candidate).isSuccess) {
            failure.complete(IntercomConnectionProblem("Camera candidate queue closed"))
        }
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
    override fun onAddStream(stream: MediaStream) = Unit
    override fun onRemoveStream(stream: MediaStream) = Unit
    override fun onDataChannel(channel: DataChannel) = Unit
    override fun onRenegotiationNeeded() = Unit
}

private suspend fun PeerConnection.createLocalOffer(): SessionDescription {
    val offer = suspendCancellableCoroutine { result ->
        createOffer(object : DescriptionObserver() {
            override fun onCreateSuccess(description: SessionDescription) = result.resume(description)
            override fun fail() = result.resumeWithException(HaProblem("Couldn't create a WebRTC offer"))
        }, MediaConstraints())
    }
    setDescription(offer, local = true)
    return offer
}

private suspend fun PeerConnection.applyAnswer(sdp: String) {
    setDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp), local = false)
}

private suspend fun PeerConnection.setDescription(description: SessionDescription, local: Boolean) =
    suspendCancellableCoroutine<Unit> { result ->
        val callback = object : DescriptionObserver() {
            override fun onSetSuccess() = result.resume(Unit)
            override fun fail() = result.resumeWithException(HaProblem("WebRTC negotiation failed"))
        }
        if (local) setLocalDescription(callback, description) else setRemoteDescription(callback, description)
    }

private abstract class DescriptionObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String) = fail()
    override fun onSetFailure(error: String) = fail()
    abstract fun fail()
}
