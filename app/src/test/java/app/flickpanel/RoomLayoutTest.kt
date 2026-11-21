package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLayoutTest {
    @Test
    fun hidingHeatingKeepsTheChosenRoomsAndTheirLightsInOrder() {
        val kitchen = Area("kitchen", "Kitchen")
        val hallway = Area("hallway", "Hallway")
        val study = Area("study", "Study")
        val catalogue = Catalogue(
            entries = listOf(
                Entry(Entity("light.kitchen", "on"), kitchen.id, diagnostic = false, visible = true),
                Entry(Entity("climate.kitchen", "heat"), kitchen.id, diagnostic = false, visible = true),
                Entry(Entity("light.hallway", "off"), hallway.id, diagnostic = false, visible = true),
                Entry(Entity("climate.study", "heat"), study.id, diagnostic = false, visible = true),
            ),
            areas = listOf(hallway, kitchen, study),
        )
        val config = PanelConfig(roomIds = listOf(kitchen.id, study.id, hallway.id), showRoomHeating = false)
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(config))
        val sections = RoomLayout(restored, catalogue).sections
        assertEquals(listOf(kitchen, hallway), sections.map { it.area })
        assertEquals(listOf("light.kitchen", "light.hallway"), sections.flatMap { room -> room.tiles.map { it.entity } })
        assertTrue(PanelConfigCodec.decode("""{"version":1}""").showRoomHeating)
    }
}
