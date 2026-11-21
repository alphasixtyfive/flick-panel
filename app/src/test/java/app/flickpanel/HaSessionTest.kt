package app.flickpanel

import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.Link
import app.flickpanel.ha.MediaChoice
import app.flickpanel.ha.decodeHistory
import app.flickpanel.ha.parseServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HaSessionTest {
    @Test fun mediaFoldersUseTheCategoryHomeAssistantReports() {
        val row = JSONObject("""{
            "media_content_id":"opaque/category",
            "media_content_type":"provider",
            "title":"Meine Musik",
            "media_class":"directory",
            "children_media_class":"artist",
            "can_expand":true
        }""")
        assertEquals("artist", MediaChoice.from(row)?.category)
        row.remove("children_media_class")
        assertEquals("directory", MediaChoice.from(row)?.category)
        row.put("media_class", "album").put("children_media_class", "track")
        assertEquals("album", MediaChoice.from(row)?.category)
    }

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
                val catalogue = withContext(Dispatchers.Default) { client.catalogue() }
                assertEquals("Kitchen", catalogue.areas.single().name)
                assertEquals("light.lamp", catalogue.room("kitchen").single().entity.id)
                val music = client.browseMusic("media_player.kitchen", null)
                assertEquals("Radio favourites", music.single().title)
                assertEquals(true, music.single().playable)
                assertEquals("/api/media_player_proxy/cover", music.single().artwork)
                assertEquals("playlist", music.single().category)
                assertNotNull(client.artwork(requireNotNull(music.single().artwork)))
                client.command("media_player.kitchen", "media_player", "play_media", JSONObject()
                    .put("media_content_id", music.single().id).put("media_content_type", music.single().type))
                client.command("light.lamp", "light", "turn_on", JSONObject().put("brightness_pct", 50))
                withTimeout(6_000) { client.entities.first { it["light.lamp"]?.state == "on" } }
                assertEquals(50, client.entities.value.getValue("light.lamp").brightness)
                assertEquals(listOf(801.0, 840.0), client.history("sensor.power").map { it.value })
                assertEquals("/api/hls/abcdefgh123456/master_playlist.m3u8", client.stream("camera.front_door").encodedPath)
                assertNotNull(client.snapshot("camera.front_door"))
                assertNotNull(client.snapshot("image.vacuum_map"))
                val album = client.photoAlbum("media-source://synology_dsm/nas/album")
                assertEquals(listOf("media-source://synology_dsm/nas/album/photo"), album.photos)
                assertEquals("cover photo", client.photo(album.photos.single())?.decodeToString())
                client.select(setOf("sensor.power"))
                withTimeout(6_000) { client.entities.first { it.keys == setOf("sensor.power") } }
                assertFalse(client.entities.value.containsKey("light.lamp"))
                assertEquals(1, fixture.connections.get())
            } finally { client.stop() }
        }
    }

    @Test fun musicAssistantCoverProxyNeverReceivesTheHomeAssistantToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("fixture cover"))
            val client = HaClient(HaCredentials(parseServer("http://127.0.0.1:8123"), "test-fixture-token"), this)
            val cover = server.url("/imageproxy/cover").newBuilder().host("127.0.0.1").build()
            assertNotNull(client.artwork(cover.toString()))
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertNull(client.artwork("http://192.168.1.2:8095/imageproxy/cover"))
        }
    }

    @Test fun personAvatarUsesTheHomeAssistantImageEndpointAndRejectsRemoteReferences() = runBlocking {
        MockWebServer().use { server ->
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("fixture avatar"))
            val client = HaClient(HaCredentials(address, "test-fixture-token"), this)

            val reference = "/api/image/serve/profile-id/512x512"
            assertEquals("fixture avatar", client.personAvatar(reference)?.decodeToString())
            val request = server.takeRequest()
            assertEquals(reference, request.path)
            assertEquals("Bearer test-fixture-token", request.getHeader("Authorization"))
            assertNull(client.personAvatar("https://cdn.example.invalid$reference"))
            assertEquals(1, server.requestCount)
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

    @Test fun reconnectsIfAnAcknowledgedSubscriptionNeverSendsItsSnapshot() = runBlocking {
        val fixture = FakeHome(omitFirstSnapshot = true)
        MockWebServer().use { server ->
            server.dispatcher = fixture
            server.start()
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "test-fixture-token"), this)
            try {
                client.select(setOf("sensor.power"))
                client.start()
                withTimeout(15_000) { client.link.first { it == Link.ONLINE } }
                assertEquals(2, fixture.connections.get())
                assertEquals("840", client.entities.value.getValue("sensor.power").state)
            } finally {
                client.stop()
            }
        }
    }

    @Test fun historyStaysBoundedAndKeepsTheEntireTimeRange() {
        val rows = JSONArray((319 downTo 1).map { point ->
            JSONObject().put("s", point.toString()).put("lu", point)
        })
        val points = decodeHistory(rows)
        assertEquals(160, points.size)
        assertEquals(1_000L, points.first().time)
        assertEquals(319_000L, points.last().time)
        assertEquals(points.sortedBy { it.time }, points)
    }
}

private class FakeHome(private val omitFirstSnapshot: Boolean = false) : Dispatcher() {
    val connections = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
        "/api/" -> authorized(request, MockResponse().setBody("""{"message":"API running."}"""))
        "/api/camera_proxy/camera.front_door", "/api/image_proxy/image.vacuum_map", "/api/media_player_proxy/cover" -> authorized(request,
            MockResponse().setHeader("Content-Type", "image/jpeg").setBody("fixture image bytes"))
        "/synology_dsm/nas/cover.jpg" -> authorized(request,
            MockResponse().setHeader("Content-Type", "image/jpeg").setBody("cover photo"))
        "/api/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            private var subscription = 0
            private var lastId = 0
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connections.incrementAndGet()
                webSocket.send("""{"type":"auth_required"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                val id = message.optInt("id")
                if (message.optString("type") != "auth") {
                    if (id <= lastId) {
                        webSocket.send("""{"id":$id,"type":"result","success":false,"error":{"code":"id_reuse"}}""")
                        return
                    }
                    lastId = id
                }
                fun result(value: String = "null") =
                    webSocket.send("""{"id":$id,"type":"result","success":true,"result":$value}""")
                when (message.getString("type")) {
                    "auth" -> webSocket.send(if (message.optString("access_token") == "test-fixture-token")
                        """{"type":"auth_ok"}""" else """{"type":"auth_invalid"}""")
                    "subscribe_entities" -> {
                        val ids = message.getJSONArray("entity_ids").toString()
                        subscription = id
                        result()
                        if (omitFirstSnapshot && connections.get() == 1) return
                        val light = if (ids.contains("light.lamp")) """"light.lamp":{"s":"off"},""" else ""
                        webSocket.send("""{"id":$id,"type":"event","event":{"a":{$light"sensor.power":{"s":"840","a":{"unit_of_measurement":"W"}}}}}""")
                    }
                    "unsubscribe_events" -> result()
                    "get_states" -> result("""[{"entity_id":"light.lamp","state":"off","attributes":{"friendly_name":"Lamp","supported_color_modes":["brightness"]}}]""")
                    "config/entity_registry/list_for_display" -> result("""{"entities":[{"ei":"light.lamp","di":"h1"}]}""")
                    "config/device_registry/list" -> result("""[{"id":"h1","area_id":"kitchen"}]""")
                    "config/area_registry/list" -> result("""[{"area_id":"kitchen","name":"Kitchen"}]""")
                    "media_player/browse_media" -> result("""{"children":[
                        {"media_content_id":"library://playlist/1","media_content_type":"playlist","title":"Radio favourites","media_class":"playlist","can_play":true,"thumbnail":"/api/media_player_proxy/cover"},
                        {"media_content_id":"camera.front_gate","media_content_type":"video","title":"Front gate","media_class":"video","can_play":true}
                    ]}""")
                    "media_source/browse_media" -> result("""{
                        "media_content_id":"media-source://synology_dsm","title":"Synology Photos","children":[
                        {"media_content_id":"media-source://synology_dsm/nas/album/photo","media_content_type":"image/jpeg","can_play":true},
                        {"media_content_id":"media-source://synology_dsm/nas/album/video","media_content_type":"video/mp4","can_play":true}
                    ]}""")
                    "media_source/resolve_media" -> result("""{"url":"/synology_dsm/nas/cover.jpg","mime_type":"image/jpeg"}""")
                    "call_service" -> {
                        val target = message.getJSONObject("target").getString("entity_id")
                        val data = message.getJSONObject("service_data")
                        if (message.getString("domain") == "media_player") {
                            assertEquals("media_player.kitchen", target)
                            assertEquals("play_media", message.getString("service"))
                            assertEquals("library://playlist/1", data.getString("media_content_id"))
                            assertEquals("playlist", data.getString("media_content_type"))
                            result()
                        } else {
                            assertEquals("light.lamp", target)
                            assertEquals(50, data.getInt("brightness_pct"))
                            result()
                            webSocket.send("""{"id":$subscription,"type":"event","event":{"c":{"light.lamp":{"+":{"s":"on","a":{"brightness":128}}}}}}""")
                        }
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
