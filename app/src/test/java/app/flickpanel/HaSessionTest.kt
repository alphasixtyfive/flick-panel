package app.flickpanel

import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.Link
import app.flickpanel.ha.parseServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HaSessionTest {
    @Test fun discoversControlsAndUsesOneSocketForStateActionsHistoryAndMedia() = runBlocking {
        val fixture = FakeHome()
        MockWebServer().use { server ->
            server.dispatcher = fixture
            server.start()
            val credentials = HaCredentials(parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString()), "test-fixture-token")
            HaClient.validate(credentials)
            val client = HaClient(credentials, this)
            try {
                client.select(setOf("light.lamp", "sensor.power"))
                client.start()
                withTimeout(6_000) { client.link.first { it == Link.ONLINE } }
                assertEquals("off", client.entities.value.getValue("light.lamp").state)
                val catalogue = client.catalogue()
                assertEquals("Kitchen", catalogue.areas.single().name)
                assertEquals("light.lamp", catalogue.room("kitchen").single().entity.id)
                client.command("light.lamp", "light", "turn_on", JSONObject().put("brightness_pct", 50))
                withTimeout(6_000) { client.entities.first { it["light.lamp"]?.state == "on" } }
                assertEquals(50, client.entities.value.getValue("light.lamp").brightness)
                assertEquals(listOf(801.0, 840.0), client.history("sensor.power").map { it.value })
                assertEquals("/api/hls/abcdefgh123456/master_playlist.m3u8", client.stream("camera.front_door").encodedPath)
                assertNotNull(client.snapshot("camera.front_door"))
                client.select(setOf("sensor.power"))
                withTimeout(6_000) { client.entities.first { it.keys == setOf("sensor.power") } }
                assertFalse(client.entities.value.containsKey("light.lamp"))
                assertEquals(1, fixture.connections.get())
            } finally { client.stop() }
        }
    }

    @Test fun rejectedTokenNeverBecomesOnline() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = FakeHome()
            server.start()
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "wrong-token"), this)
            try {
                client.start()
                withTimeout(6_000) { client.link.first { it == Link.AUTH_REJECTED } }
                assertEquals(emptyMap<String, Any>(), client.entities.value)
            } finally { client.stop() }
        }
    }
}

private class FakeHome : Dispatcher() {
    val connections = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
        "/api/" -> authorized(request, MockResponse().setBody("""{"message":"API running."}"""))
        "/api/camera_proxy/camera.front_door" -> authorized(request,
            MockResponse().setHeader("Content-Type", "image/jpeg").setBody("fixture image bytes"))
        "/api/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            private var subscription = 0
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connections.incrementAndGet()
                webSocket.send("""{"type":"auth_required"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                val id = message.optInt("id")
                fun result(value: String = "null") =
                    webSocket.send("""{"id":$id,"type":"result","success":true,"result":$value}""")
                when (message.getString("type")) {
                    "auth" -> webSocket.send(if (message.optString("access_token") == "test-fixture-token")
                        """{"type":"auth_ok"}""" else """{"type":"auth_invalid"}""")
                    "subscribe_entities" -> {
                        val ids = message.getJSONArray("entity_ids").toString()
                        subscription = id
                        result()
                        val light = if (ids.contains("light.lamp")) """"light.lamp":{"s":"off"},""" else ""
                        webSocket.send("""{"id":$id,"type":"event","event":{"a":{$light"sensor.power":{"s":"840","a":{"unit_of_measurement":"W"}}}}}""")
                    }
                    "unsubscribe_events" -> result()
                    "get_states" -> result("""[{"entity_id":"light.lamp","state":"off","attributes":{"friendly_name":"Lamp","supported_color_modes":["brightness"]}}]""")
                    "config/entity_registry/list" -> result("""[{"entity_id":"light.lamp","device_id":"h1"}]""")
                    "config/device_registry/list" -> result("""[{"id":"h1","area_id":"kitchen"}]""")
                    "config/area_registry/list" -> result("""[{"area_id":"kitchen","name":"Kitchen"}]""")
                    "call_service" -> {
                        assertEquals("light.lamp", message.getJSONObject("target").getString("entity_id"))
                        assertEquals(50, message.getJSONObject("service_data").getInt("brightness_pct"))
                        result()
                        webSocket.send("""{"id":$subscription,"type":"event","event":{"c":{"light.lamp":{"+":{"s":"on","a":{"brightness":128}}}}}}""")
                    }
                    "history/history_during_period" -> result("""{"sensor.power":[{"s":"801","lu":1780000000},{"s":"840","lu":1780000100}]}""")
                    "camera/stream" -> result("""{"url":"/api/hls/abcdefgh123456/master_playlist.m3u8"}""")
                }
            }
        })
        else -> MockResponse().setResponseCode(404)
    }

    private fun authorized(request: RecordedRequest, response: MockResponse): MockResponse =
        if (request.getHeader("Authorization") == "Bearer test-fixture-token") response else MockResponse().setResponseCode(401)
}
