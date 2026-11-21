package app.flickpanel

import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBadgesTest {
    private val maintenance = HomeBadge("binary_sensor.maintenance", "Needs attention", listOf("on"))
    private val pollen = HomeBadge("sensor.pollen", "High pollen", listOf("high", "very-high"))
    private val layout = RoomLayout(config = PanelConfig(homeBadges = listOf(maintenance, pollen)))

    @Test fun legacyLayoutsDropCompactReadingsAndKeepAlerts() {
        val config = PanelConfigCodec.decode("""
            {
              "version": 1,
              "tiles": [
                { "entity": "sensor.temperature", "kind": "GLANCE" },
                { "entity": "sensor.power", "kind": "TREND" }
              ],
              "homeBadges": [
                { "entity": "binary_sensor.maintenance", "label": "Needs attention", "states": ["on"] }
              ]
            }
        """.trimIndent())

        assertEquals(listOf("sensor.power"), config.favourites.map { it.entity })
        assertEquals(listOf(maintenance), config.homeBadges)
    }

    @Test fun showOnlyActiveLiveStates() {
        val ui = PanelUi(layout = layout, link = Link.ONLINE, entities = mapOf(
            maintenance.entity to Entity(maintenance.entity, "off"),
            pollen.entity to Entity(pollen.entity, "very-high"),
        ))
        assertEquals(listOf("High pollen"), ui.activeHomeBadges().map { it.label })
        assertTrue(ui.copy(link = Link.OFFLINE).activeHomeBadges().isEmpty())
        assertTrue(ui.copy(entities = mapOf(pollen.entity to Entity(pollen.entity, "unavailable"))).activeHomeBadges().isEmpty())
    }

    @Test fun reuseMaintenanceItemsAndPersistRules() {
        val issue = JSONObject().put("name", "Low batteries").put("value", "Kitchen window")
        val entity = Entity(maintenance.entity, "on", JSONObject().put("items", JSONArray(listOf(issue))))
        assertEquals(listOf("Low batteries" to "Kitchen window"), ActiveHomeBadge("Needs attention", entity).details())
        assertEquals(layout.config, PanelConfigCodec.decode(PanelConfigCodec.encode(layout.config)))
    }
}
