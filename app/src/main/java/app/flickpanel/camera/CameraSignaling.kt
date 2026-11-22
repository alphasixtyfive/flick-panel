package app.flickpanel.camera

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

internal interface CameraSignaling {
    val name: String
    val audio: CameraAudioOffer
    val iceServers: List<PeerConnection.IceServer> get() = emptyList()
    val relayOnly: Boolean get() = false
    val dataChannel: String? get() = null

    fun exchange(offer: String, candidates: ReceiveChannel<IceCandidate>): Flow<CameraSignal>
}

internal enum class CameraAudioOffer { LISTEN, TWO_WAY, SEPARATE_TALK }

internal sealed interface CameraSignal {
    data class Answer(val sdp: String) : CameraSignal
    data class Candidate(val candidate: IceCandidate) : CameraSignal
}
