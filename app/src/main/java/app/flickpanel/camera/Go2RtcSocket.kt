package app.flickpanel.camera

import app.flickpanel.ha.HaHttp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.IceCandidate

internal class Go2RtcSocket(url: HttpUrl) : CameraSignaling, AutoCloseable {
    override val name = "go2rtc"
    override val audio = CameraAudioOffer.SEPARATE_TALK
    private val opened = CompletableDeferred<Unit>()
    private val inbox = Channel<String>(64)
    private val socket = HaHttp.webSockets.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length <= 96_000 && inbox.trySend(text).isSuccess) return
                inbox.close(IntercomConnectionProblem("Invalid intercom response", retryable = false))
                webSocket.cancel()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                disconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                response?.close()
                val problem = when (code) {
                    null -> IntercomConnectionProblem("Intercom proxy connection failed")
                    401, 403 -> IntercomConnectionProblem("Intercom proxy denied access", retryable = false)
                    404 -> IntercomConnectionProblem("Intercom proxy was not found", retryable = false)
                    else -> IntercomConnectionProblem("Intercom proxy returned HTTP $code", retryable = code >= 500)
                }
                disconnected(problem)
            }
        }
    )

    override fun exchange(offer: String, candidates: ReceiveChannel<IceCandidate>): Flow<CameraSignal> = flow {
        opened.await()
        send("webrtc/offer", offer)
        coroutineScope {
            launch { for (candidate in candidates) send("webrtc/candidate", candidate.sdp) }
            for (text in inbox) {
                val message = JSONObject(text)
                val value = message.optString("value")
                when (message.optString("type")) {
                    "webrtc/answer" -> emit(CameraSignal.Answer(value))
                    "webrtc/candidate" -> if (value.isNotBlank()) emit(CameraSignal.Candidate(IceCandidate("0", 0, value)))
                    "error" -> throw IntercomConnectionProblem("The intercom stream couldn't connect", retryable = false)
                }
            }
        }
    }

    private fun send(type: String, value: String) {
        if (!socket.send(JSONObject().put("type", type).put("value", value).toString())) {
            throw IntercomConnectionProblem("Intercom connection closed")
        }
    }

    private fun disconnected(problem: IntercomConnectionProblem = IntercomConnectionProblem("Intercom connection closed")) {
        opened.completeExceptionally(problem)
        inbox.close(problem)
    }

    override fun close() {
        socket.cancel()
        inbox.cancel()
    }
}
