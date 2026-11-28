package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.Link
import app.flickpanel.ha.VacuumAction
import app.flickpanel.ha.canRunVacuum
import app.flickpanel.ha.parseServer
import app.flickpanel.ha.vacuumBatteryPercent
import app.flickpanel.ha.vacuumPrimaryAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.util.concurrent.LinkedBlockingQueue

class VacuumTest {
    private val tile = TileSpec("vacuum.robot", TileKind.VACUUM,
        vacuumMap = "image.robot_map", vacuumBattery = "sensor.robot_battery")

    @Test fun layoutKeepsOneVacuumAndOnlySubscribesToTheMapWhileOpen() {
        val config = PanelConfig(favourites = listOf(tile))
        assertEquals(config, PanelConfigCodec.decode(PanelConfigCodec.encode(config)))
        val invalid = config.copy(favourites = listOf(
            tile.copy(vacuumMap = "sensor.wrong_domain"), tile, tile.copy(entity = "vacuum.second"),
        ))
        assertEquals(config, PanelConfigCodec.decode(PanelConfigCodec.encode(invalid)))
        val dashboard = PanelUi(layout = RoomLayout(config))
        assertEquals(setOf(tile.entity, tile.vacuumBattery), dashboard.liveEntityIds(roomsRequested = false))
        val opened = dashboard.showViewer(PanelViewer.Vacuum(tile.entity))
        assertEquals(dashboard.liveEntityIds(roomsRequested = false) + tile.vacuumMap!!,
            opened.liveEntityIds(roomsRequested = false))
    }

    @Test fun controlsFollowCapabilitiesAndVacuumState() {
        val docked = Entity(tile.entity, "docked", JSONObject()
            .put("supported_features", VacuumAction.entries.sumOf(VacuumAction::feature))
            .put("battery_level", 73))
        assertEquals(VacuumAction.START, docked.vacuumPrimaryAction(false))
        assertEquals(VacuumAction.CLEAN_AREA, docked.vacuumPrimaryAction(true))
        assertFalse(docked.canRunVacuum(VacuumAction.DOCK))
        val cleaning = docked.copy(state = "cleaning")
        assertEquals(VacuumAction.PAUSE, cleaning.vacuumPrimaryAction(true))
        assertFalse(cleaning.canRunVacuum(VacuumAction.CLEAN_AREA))
        assertTrue(cleaning.canRunVacuum(VacuumAction.STOP))
        val paused = docked.copy(state = "paused")
        assertEquals(VacuumAction.START, paused.vacuumPrimaryAction(false))
        assertTrue(paused.canRunVacuum(VacuumAction.DOCK))
        assertNull(docked.copy(state = "unavailable").vacuumPrimaryAction(false))
        val areaOnly = docked.copy(attributes = JSONObject().put("supported_features", VacuumAction.CLEAN_AREA.feature))
        assertNull(areaOnly.vacuumPrimaryAction(false))
        assertEquals(88, vacuumBatteryPercent(docked, Entity(tile.vacuumBattery!!, "87.6")))
        assertEquals(73, vacuumBatteryPercent(docked, Entity(tile.vacuumBattery, "unavailable")))
    }

    @Test fun roomNamesAndCleaningUseTheNativeHomeAssistantRequests() = runBlocking {
        val requests = LinkedBlockingQueue<JSONObject>()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(VacuumFixture(requests)))
            server.start()
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "vacuum-fixture-token"), this)
            try {
                client.select(setOf(tile.entity))
                client.start()
                withTimeout(6_000) { client.connectionState.first { it.link == Link.ONLINE } }
                val rooms = client.vacuumRooms(tile.entity)
                assertEquals(listOf("Kitchen", "Study"), rooms.map { it.name })
                client.command(tile.entity, "vacuum", VacuumAction.CLEAN_AREA.service, VacuumAction.CLEAN_AREA.data(rooms.map { it.id }))
                val sent = requests.toList()
                assertEquals(listOf("config/entity_registry/get", "config/area_registry/list", "call_service"),
                    sent.map { it.getString("type") })
                assertEquals(tile.entity, sent[0].getString("entity_id"))
                val clean = sent.last()
                assertEquals("clean_area", clean.getString("service"))
                assertEquals(tile.entity, clean.getJSONObject("target").getString("entity_id"))
                val areas = clean.getJSONObject("service_data").getJSONArray("cleaning_area_id")
                assertEquals(listOf("kitchen", "study"), (0 until areas.length()).map(areas::getString))
                assertFalse(VacuumAction.START.data(rooms.map { it.id }).has("cleaning_area_id"))
            } finally {
                client.stop()
            }
        }
    }
}

private class VacuumFixture(private val requests: LinkedBlockingQueue<JSONObject>) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send("""{"type":"auth_required"}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val request = JSONObject(text)
        val id = request.optInt("id")
        fun result(payload: String = "null") =
            webSocket.send("""{"id":$id,"type":"result","success":true,"result":$payload}""")
        when (request.getString("type")) {
            "auth" -> webSocket.send("""{"type":"auth_ok"}""")
            "subscribe_entities" -> {
                result()
                webSocket.send("""{"id":$id,"type":"event","event":{"a":{"vacuum.robot":{"s":"docked"}}}}""")
            }
            "config/entity_registry/get" -> {
                requests.add(request)
                result("""{"options":{"vacuum":{"area_mapping":{"study":["2"],"kitchen":["1"],"old_area":["3"]}}}}""")
            }
            "config/area_registry/list" -> {
                requests.add(request)
                result("""[{"area_id":"study","name":"Study"},{"area_id":"hall","name":"Hall"},
                    {"area_id":"kitchen","name":"Kitchen"}]""")
            }
            "call_service" -> {
                requests.add(request)
                result()
            }
        }
    }
}
