package app.flickpanel.ha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class HaSocket(client: OkHttpClient, private val credentials: HaCredentials) {
    private val inbox = Channel<String>(capacity = 64)
    private val requests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val sendLock = Any()
    private var nextId = 0
    private val socket = client.newWebSocket(
        Request.Builder().url(credentials.server.resolve("api/websocket")!!).build(),
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 12_000_000 || !inbox.trySend(text).isSuccess) {
                    inbox.close(HaProblem("Home Assistant sent more data than the panel could read"))
                    webSocket.cancel()
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                response?.close()
                inbox.close(HaProblem("Home Assistant is unreachable"))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                inbox.close()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { inbox.close() }
        }
    )

    suspend fun authenticate(): Boolean {
        return withTimeoutOrNull(8_000) {
            val first = receive()
            if (first.optString("type") != "auth_required") throw HaProblem("Invalid Home Assistant handshake")
            send(JSONObject().put("type", "auth").put("access_token", credentials.token))
            when (receive().optString("type")) {
                "auth_ok" -> true
                "auth_invalid" -> false
                else -> throw HaProblem("Invalid Home Assistant handshake")
            }
        } ?: throw HaProblem("Home Assistant sign-in timed out")
    }

    suspend fun pump(event: (JSONObject) -> Unit) {
        for (text in inbox) {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "result" -> requests.remove(msg.optInt("id"))?.complete(msg)
                "event" -> event(msg)
            }
        }
        throw HaProblem("Home Assistant disconnected")
    }

    suspend fun request(type: String, data: JSONObject = JSONObject(), onSend: (Int) -> Unit = {}): JSONObject {
        val reply = CompletableDeferred<JSONObject>()
        // Home Assistant requires IDs to increase in wire order, including for concurrent calls.
        val id = synchronized(sendLock) {
            val requestId = ++nextId
            requests[requestId] = reply
            try {
                onSend(requestId)
                send(data.put("type", type).put("id", requestId))
            } catch (error: Exception) {
                requests.remove(requestId)
                throw error
            }
            requestId
        }
        try {
            val result = withTimeoutOrNull(8_000) { reply.await() }
                ?: throw HaProblem("Home Assistant didn't reply")
            if (!result.optBoolean("success")) throw HaProblem(
                when (result.optJSONObject("error")?.optString("code")) {
                    "unauthorized" -> "This Home Assistant user isn't allowed to do that"
                    "unknown_command" -> "This Home Assistant server doesn't support this feature"
                    "id_reuse" -> "Home Assistant received an out-of-order request"
                    else -> "Home Assistant couldn't complete the request"
                }
            )
            return result
        } finally { requests.remove(id) }
    }

    fun close() {
        inbox.close()
        socket.cancel()
        for (reply in requests.values) reply.completeExceptionally(HaProblem("Home Assistant disconnected"))
        requests.clear()
    }

    private fun send(json: JSONObject) {
        if (!socket.send(json.toString())) throw HaProblem("Home Assistant disconnected")
    }

    private suspend fun receive() = JSONObject(inbox.receive())
}
