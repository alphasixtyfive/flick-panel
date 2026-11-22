package app.flickpanel.ha

import app.flickpanel.camera.CameraAudioOffer
import app.flickpanel.camera.CameraSignal
import app.flickpanel.camera.CameraSignaling
import app.flickpanel.camera.IntercomConnectionProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

internal data class CameraCapabilities(val webRtc: Boolean, val twoWayAudio: Boolean) {
    companion object {
        val None = CameraCapabilities(false, false)

        fun from(result: JSONObject): CameraCapabilities {
            val streams = result.optJSONArray("frontend_stream_types")
            val webRtc = streams != null && (0 until streams.length()).any { streams.optString(it) == "web_rtc" }
            return CameraCapabilities(webRtc, webRtc && result.opt("two_way_audio") == true)
        }
    }
}

internal class NativeCameraSignaling(
    private val socket: HaSocket,
    private val entity: String,
    config: JSONObject,
    override val audio: CameraAudioOffer,
) : CameraSignaling {
    override val name = "ha_webrtc"
    private val configuration = config.optJSONObject("configuration")
    override val iceServers = configuration?.optJSONArray("iceServers").objects().mapNotNull { server ->
        val single = server.opt("urls") as? String
        val multiple = server.optJSONArray("urls")
        val urls = if (single != null) listOf(single) else
            (0 until (multiple?.length() ?: 0)).mapNotNull { multiple?.opt(it) as? String }
        val valid = urls.filter { it.substringBefore(':') in setOf("stun", "stuns", "turn", "turns") }
        if (valid.isEmpty()) null else PeerConnection.IceServer.builder(valid)
            .setUsername(server.text("username").orEmpty()).setPassword(server.text("credential").orEmpty())
            .createIceServer()
    }.toList()
    override val relayOnly = configuration?.optString("iceTransportPolicy") == "relay"
    override val dataChannel = config.text("dataChannel")

    override fun exchange(offer: String, candidates: ReceiveChannel<IceCandidate>): Flow<CameraSignal> = flow {
        coroutineScope {
            val session = CompletableDeferred<String>()
            launch {
                val id = session.await()
                for (candidate in candidates) {
                    socket.request("camera/webrtc/candidate", JSONObject().put("entity_id", entity)
                        .put("session_id", id).put("candidate", JSONObject().put("candidate", candidate.sdp)
                            .put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex)))
                }
            }
            val request = JSONObject().put("entity_id", entity).put("offer", offer)
            socket.events("camera/webrtc/offer", request).collect { event ->
                when (event.optString("type")) {
                    "session" -> event.text("session_id")?.let { session.complete(it) }
                    "answer" -> event.text("answer")?.let { emit(CameraSignal.Answer(it)) }
                    "candidate" -> event.optJSONObject("candidate")?.let { candidate ->
                        decodeNativeIceCandidate(candidate)?.let { emit(CameraSignal.Candidate(it)) }
                    }
                    "error" -> throw IntercomConnectionProblem("Home Assistant couldn't start WebRTC", retryable = false)
                }
            }
        }
    }
}

internal fun decodeNativeIceCandidate(candidate: JSONObject): IceCandidate? {
    val sdp = candidate.text("candidate")?.takeIf { it.startsWith("candidate:") } ?: return null
    val mid = candidate.text("sdpMid")
    val index = (candidate.opt("sdpMLineIndex") as? Number)?.toInt()
    if (mid == null && (index == null || index < 0)) return null
    // The Android JNI binding needs a non-null media ID even for an index-only candidate.
    return IceCandidate(mid.orEmpty(), index ?: -1, sdp)
}
