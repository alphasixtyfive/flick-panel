package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Entry
import app.flickpanel.ha.fanPowerService
import app.flickpanel.ha.fanSpeed
import app.flickpanel.ha.fanSpeedTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalTilesTest {
    @Test
    fun fanUsesReportedControlsAndSteps() {
        val fan = Entity("fan.bedroom", "off", JSONObject().put("supported_features", 49).put("percentage_step", 25))
        assertEquals(listOf(TileKind.FAN), fan.kinds())
        assertEquals("turn_on", fan.fanPowerService(true))
        assertEquals("turn_off", fan.fanPowerService(false))
        assertEquals(0, fan.fanSpeed)
        assertEquals(50, fan.fanSpeedTarget(54))
        assertEquals(100, fan.fanSpeedTarget(100))
        assertNull(fan.copy(state = "unavailable").fanSpeedTarget(50))
        val basic = fan.copy(attributes = JSONObject().put("supported_features", 32))
        assertNull(basic.fanPowerService(false))
        assertNull(basic.fanSpeedTarget(50))
    }

    @Test
    fun percentagesRequireAnActualPercentageSensorAndValidReading() {
        val sensor = Entity("sensor.battery", "24.5", JSONObject().put("unit_of_measurement", "%"))
        assertTrue(TileKind.PERCENTAGE in sensor.kinds())
        assertEquals(24.5f, sensor.percentageReading)
        assertNull(sensor.copy(state = "103").percentageReading)
        assertNull(sensor.copy(state = "unknown").percentageReading)
        assertTrue(TileKind.PERCENTAGE !in sensor.copy(attributes = JSONObject().put("unit_of_measurement", "W")).kinds())
        val config = PanelConfig(favourites = listOf(TileSpec(sensor.id, TileKind.PERCENTAGE)))
        assertEquals(config, PanelConfigCodec.decode(PanelConfigCodec.encode(config)))
    }

    @Test
    fun roomControlsOptInAndSurviveBackup() {
        val room = Area("kitchen", "Kitchen")
        val fan = Entity("fan.filter", "on")
        val cover = Entity("cover.blind", "closed")
        val selector = Entity("input_select.mode", "eco")
        val entries = listOf(fan, cover, selector).map { Entry(it, room.id, diagnostic = false, visible = true) }
        val catalogue = Catalogue(entries, listOf(room))
        val config = PanelConfig(roomIds = listOf(room.id))
        assertTrue(RoomLayout(config, catalogue).controls(room).all { it.preference.hidden })
        val chosen = config.copy(roomTiles = mapOf(room.id to entries.map { RoomTilePreference(it.entity.id) }))
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(chosen))
        assertEquals(listOf(TileKind.FAN, TileKind.COVER, TileKind.CHOICE),
            RoomLayout(restored, catalogue).sections.single().tiles.map(TileSpec::kind))
    }
}
