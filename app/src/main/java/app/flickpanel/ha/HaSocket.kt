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
import java.util.concurrent.atomic.AtomicLong

private const val RESPONSE_TIMEOUT_MS = 8_000L
private const val MAX_MESSAGE_CHARS = 12_000_000
private const val MAX_QUEUED_CHARS = 24_000_000L

internal class HaSocket(client: OkHttpClient, private val credentials: HaCredentials) {
    private val inbox = Channel<String>(capacity = 64)
    private val queuedChars = AtomicLong()
    private val requests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val sendLock = Any()
    private var nextId = 0
    private val socket = client.newWebSocket(
        Request.Builder().url(credentials.server.resolve("api/websocket")!!).build(),
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val length = text.length.toLong()
                val total = queuedChars.addAndGet(length)
                if (length <= MAX_MESSAGE_CHARS && total <= MAX_QUEUED_CHARS && inbox.trySend(text).isSuccess) return
                queuedChars.addAndGet(-length)
                inbox.close(HaProblem("Home Assistant sent more data than the panel could read"))
                webSocket.cancel()
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

    suspend fun authenticate(): Boolean =
        withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
            val first = receive()
            if (first.optString("type") != "auth_required") throw HaProblem("Invalid Home Assistant handshake")
            send(JSONObject().put("type", "auth").put("access_token", credentials.token))
            when (receive().optString("type")) {
                "auth_ok" -> true
                "auth_invalid" -> false
                else -> throw HaProblem("Invalid Home Assistant handshake")
            }
        } ?: throw HaProblem("Home Assistant sign-in timed out")

    suspend fun pump(event: (JSONObject) -> Unit) {
        for (text in inbox) {
            val msg = parseReceived(text)
            when (msg.optString("type")) {
                "result" -> requests.remove(msg.optInt("id"))?.complete(msg)
                "event" -> event(msg)
            }
        }
        throw HaProblem("Home Assistant disconnected")
    }

    suspend fun request(type: String, data: JSONObject = JSONObject(), onSend: (Int) -> Unit = {}): JSONObject {
        val reply = CompletableDeferred<JSONObject>()
        val message = JSONObject().apply {
            data.keys().forEach { key -> put(key, data.get(key)) }
            put("type", type)
        }
        val id = sendRequest(message, reply, onSend)
        try {
            val result = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { reply.await() }
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
        } finally {
            requests.remove(id)
        }
    }

    private fun sendRequest(message: JSONObject, reply: CompletableDeferred<JSONObject>, onSend: (Int) -> Unit): Int =
        synchronized(sendLock) {
            // Home Assistant requires IDs to increase in wire order, including for concurrent calls.
            val id = ++nextId
            requests[id] = reply
            try {
                onSend(id)
                send(message.put("id", id))
            } catch (error: Exception) {
                requests.remove(id)
                throw error
            }
            id
        }

    fun close() = synchronized(sendLock) {
        inbox.cancel()
        socket.cancel()
        for (reply in requests.values) reply.completeExceptionally(HaProblem("Home Assistant disconnected"))
        requests.clear()
    }

    private fun send(json: JSONObject) {
        if (!socket.send(json.toString())) throw HaProblem("Home Assistant disconnected")
    }

    private suspend fun receive() = parseReceived(inbox.receive())

    private fun parseReceived(text: String): JSONObject {
        queuedChars.addAndGet(-text.length.toLong())
        return JSONObject(text)
    }
}
