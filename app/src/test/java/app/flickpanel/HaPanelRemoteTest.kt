package app.flickpanel

import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelCommand
import app.flickpanel.ha.PanelRegistration
import app.flickpanel.ha.PanelRemoteState
import app.flickpanel.ha.parsePanelCommand
import app.flickpanel.ha.parseServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HaPanelRemoteTest {
    @Test fun registrationUsesExistingServerAndEnablesOnlyLocalPush() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"webhook_id":"test-private-webhook"}"""))
            val address = parseServer(server.url("/ha/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "test-access-token"), this)
            val registration = PanelRegistration("install-1234", " Kitchen Panel ", "0.1.18", "Android", "Tablet", "14")
            assertEquals("test-private-webhook", client.registerPanel(registration))
            val request = server.takeRequest()
            assertEquals("/ha/api/mobile_app/registrations", request.path)
            assertEquals("POST", request.method)
            assertEquals("Bearer test-access-token", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("app.flickpanel", body.optString("app_id"))
            assertEquals("Kitchen Panel", body.optString("device_name"))
            assertEquals("install-1234", body.optString("device_id"))
            val appData = body.getJSONObject("app_data")
            assertTrue(appData.getBoolean("push_websocket_channel"))
            assertFalse(appData.has("push_url"))
            assertFalse(appData.has("push_token"))
        }
    }

    @Test fun onlyAllowlistedPanelCommandsAreParsed() {
        assertEquals(PanelCommand.Wake, parsePanelCommand(JSONObject("""{"message":"command_flick_panel","data":{"command":"wake"}}""")))
        assertEquals(PanelCommand.Sleep, parsePanelCommand(JSONObject("""{"message":"command_flick_panel","data":{"command":"sleep"}}""")))
        assertEquals(PanelCommand.OpenCamera("camera.front_door"), parsePanelCommand(JSONObject(
            """{"message":"command_flick_panel","data":{"command":"open_camera","camera":"camera.front_door"}}""",
        )))
        assertNull(parsePanelCommand(JSONObject("""{"message":"message","data":{"command":"wake"}}""")))
        assertNull(parsePanelCommand(JSONObject("""{"message":"command_flick_panel","data":{"command":"open_camera","camera":"lock.front_door"}}""")))
        assertNull(parsePanelCommand(JSONObject("""{"message":"command_flick_panel","data":{"command":"open_camera","camera":"camera./front_door"}}""")))
        assertNull(parsePanelCommand(JSONObject("""{"message":"command_flick_panel","data":{"command":"open_gate"}}""")))
    }

    @Test fun remoteUsesDashboardSocketAndStopsOnUnlink() = runBlocking {
        MockWebServer().use { server ->
            val fixture = PanelPushFixture()
            server.enqueue(MockResponse().withWebSocketUpgrade(fixture))
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "test-access-token"), this)
            try {
                client.start()
                withTimeout(6_000) { client.link.first { it == Link.ONLINE } }
                client.setPanelWebhook("test-private-webhook")
                withTimeout(6_000) { client.panelRemoteState.first { it == PanelRemoteState.READY } }
                assertEquals(2, fixture.subscriptionAttempts.get())
                assertEquals(1, server.requestCount)
                val incoming = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(6_000) { client.panelCommands.first() }
                }
                val id = fixture.subscriptionId.get()
                val socket = fixture.connection.await()
                sendPanelEvent(socket, id + 1, "wake")
                sendPanelEvent(socket, id, "open_gate")
                sendPanelEvent(socket, id, "sleep")
                assertEquals(PanelCommand.Sleep, incoming.await())

                client.setPanelWebhook(null)
                assertEquals(PanelRemoteState.DISABLED, client.panelRemoteState.value)
                assertEquals(id, withTimeout(6_000) { fixture.unsubscribed.await() })
                assertEquals(Link.ONLINE, client.link.value)
                val stale = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(200) { client.panelCommands.first() }
                }
                sendPanelEvent(socket, id, "wake")
                assertNull(stale.await())
            } finally {
                client.stop()
            }
        }
    }

    private fun sendPanelEvent(socket: WebSocket, id: Int, command: String) {
        socket.send("""{"id":$id,"type":"event","event":{"message":"command_flick_panel","data":{"command":"$command"}}}""")
    }
}

private class PanelPushFixture : WebSocketListener() {
    val connection = CompletableDeferred<WebSocket>()
    val subscriptionAttempts = AtomicInteger()
    val subscriptionId = AtomicInteger()
    val unsubscribed = CompletableDeferred<Int>()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        connection.complete(webSocket)
        webSocket.send("""{"type":"auth_required"}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = JSONObject(text)
        val id = message.optInt("id")
        fun result() = webSocket.send("""{"id":$id,"type":"result","success":true,"result":null}""")
        when (message.optString("type")) {
            "auth" -> webSocket.send("""{"type":"auth_ok"}""")
            "mobile_app/push_notification_channel" -> {
                if (message.optString("webhook_id") != "test-private-webhook" || message.optBoolean("support_confirm", true)) return
                if (subscriptionAttempts.getAndIncrement() == 0) {
                    webSocket.send("""{"id":$id,"type":"result","success":false,"error":{"code":"not_found"}}""")
                } else {
                    subscriptionId.set(id)
                    result()
                }
            }
            "unsubscribe_events" -> {
                unsubscribed.complete(message.getInt("subscription"))
                result()
            }
        }
    }
}
