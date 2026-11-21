package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Entry
import org.json.JSONArray
import org.json.JSONObject
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

    @Test
    fun customRoomOrderVisibilitySizeAndDimmerSurviveSavingAndReset() {
        val kitchen = Area("kitchen", "Kitchen")
        val hallway = Area("hallway", "Hallway")
        val ordinary = Entity("light.everyday", "off")
        val hidden = Entity("light.status_rgb", "off")
        val dimmable = Entity("light.pendant", "off", JSONObject().put("supported_color_modes", JSONArray(listOf("brightness"))))
        val other = Entity("light.hallway", "off")
        val catalogue = Catalogue(
            entries = listOf(ordinary, hidden, dimmable).map {
                Entry(it, kitchen.id, diagnostic = false, visible = true)
            } + Entry(other, hallway.id, diagnostic = false, visible = true),
            areas = listOf(kitchen, hallway),
        )
        val config = PanelConfig(roomTiles = mapOf(kitchen.id to listOf(
            RoomTilePreference(dimmable.id, TileKind.SLIDER, wideOnTablet = true),
            RoomTilePreference(hidden.id, hidden = true),
            RoomTilePreference(ordinary.id, TileKind.SLIDER),
        )))
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(config))
        assertEquals(config.roomTiles, restored.roomTiles)
        val sections = RoomLayout(restored, catalogue).sections
        val tiles = sections.first().tiles
        assertEquals(listOf(dimmable.id, ordinary.id), tiles.map { it.entity })
        assertEquals(TileKind.SLIDER, tiles.first().kind)
        assertTrue(tiles.first().wideOnTablet)
        assertEquals(TileKind.TOGGLE, tiles.last().kind)
        assertEquals(listOf(other.id), sections.last().tiles.map { it.entity })

        val reset = RoomLayout(restored.copy(roomTiles = emptyMap()), catalogue).sections.first().tiles
        assertEquals(listOf(ordinary.id, hidden.id, dimmable.id), reset.map { it.entity })
        assertTrue(reset.all { it.kind == TileKind.TOGGLE && !it.wideOnTablet })
    }

    @Test
    fun hidingEveryTileKeepsTheRoomAndLeavesNewLightsInHiddenControls() {
        val kitchen = Area("kitchen", "Kitchen")
        val existing = RoomTilePreference("light.before", hidden = true)
        val fresh = Entity("light.new", "on")
        val catalogue = Catalogue(
            entries = listOf(Entity(existing.entity, "off"), fresh).map {
                Entry(it, kitchen.id, diagnostic = false, visible = true)
            },
            areas = listOf(kitchen),
        )
        val config = PanelConfig(roomTiles = mapOf(kitchen.id to listOf(existing)))
        val layout = RoomLayout(config, catalogue)
        assertEquals(kitchen, layout.sections.single().area)
        assertTrue(layout.sections.single().tiles.isEmpty())
        assertEquals(listOf(existing.entity, fresh.id), layout.controls(kitchen).map { it.preference.entity })
        assertTrue(layout.controls(kitchen).all { it.preference.hidden })
    }
}
