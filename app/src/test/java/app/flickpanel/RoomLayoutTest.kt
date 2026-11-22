package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileAccent
import app.flickpanel.config.TileIcon
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
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
    fun curatedRoomUsesAnExplicitCrossAreaLightAndMusicWithoutShowingAllSwitches() {
        val landing = Area("landing", "Landing")
        val stairs = Area("stairs", "Stairs")
        val catalogue = Catalogue(listOf(
            Entry(Entity("light.landing", "on"), landing.id, false, true),
            Entry(Entity("light.stairs", "off"), stairs.id, false, true),
            Entry(Entity("media_player.landing", "idle"), landing.id, false, true),
            Entry(Entity("switch.spare", "off"), landing.id, false, true),
        ), listOf(landing, stairs))
        val automatic = PanelConfig(roomIds = listOf(landing.id))
        assertEquals(listOf("light.landing"), RoomLayout(automatic, catalogue).sections.single().tiles.map { it.entity })

        val custom = automatic.copy(roomTiles = mapOf(landing.id to listOf(
            RoomTilePreference("light.stairs"),
            RoomTilePreference("media_player.landing"),
        )))
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(custom))
        val tiles = RoomLayout(restored, catalogue).sections.single().tiles
        assertEquals(listOf("light.stairs", "media_player.landing"), tiles.map { it.entity })
        assertEquals(listOf(TileKind.TOGGLE, TileKind.MEDIA), tiles.map { it.kind })
    }

    @Test
    fun trackedAreaControlsFollowAssignmentsAndKeepRoomAppearanceIfTheyReturn() {
        val kitchen = Area("kitchen", "Kitchen")
        val hall = Area("hall", "Hall")
        val pendant = Entity("light.pendant", "on")
        val lamp = Entity("light.lamp", "on")
        val config = PanelConfig(roomIds = listOf(kitchen.id, hall.id))
        fun catalogue(pendantArea: Area) = Catalogue(
            listOf(Entry(pendant, pendantArea.id, false, true), Entry(lamp, kitchen.id, false, true)),
            listOf(kitchen, hall),
        )
        val preferences = RoomLayout(config, catalogue(kitchen)).controls(kitchen).map { control ->
            if (control.preference.entity == pendant.id) {
                control.preference.copy(label = "Kitchen pendant", accent = TileAccent.AMBER, wideOnTablet = true)
            } else control.preference
        }
        val edited = config.copy(roomTiles = mapOf(kitchen.id to preferences))
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(edited))
        assertEquals(edited, restored)

        val moved = RoomLayout(restored, catalogue(hall))
        assertEquals(listOf(lamp.id), moved.sections.first().tiles.map(TileSpec::entity))
        assertEquals(listOf(pendant.id), moved.sections.last().tiles.map(TileSpec::entity))
        assertEquals(preferences, moved.controls(kitchen).map(RoomControl::preference))

        val returned = RoomLayout(restored, catalogue(kitchen)).sections.single().tiles.first { it.entity == pendant.id }
        assertEquals("Kitchen pendant", returned.label)
        assertEquals(TileAccent.AMBER, returned.accent)
        assertTrue(returned.wideOnTablet)
    }

    @Test
    fun curatedRoomsIncludeAssignedThermostatsUnlessHidden() {
        val kitchen = Area("kitchen", "Kitchen")
        val light = RoomTilePreference("light.pendant")
        val player = RoomTilePreference("media_player.kitchen")
        val thermostat = RoomTilePreference("climate.kitchen")
        val catalogue = Catalogue(listOf(
            Entry(Entity(light.entity, "on"), kitchen.id, diagnostic = false, visible = true),
            Entry(Entity(player.entity, "idle", JSONObject().put("friendly_name", "Kitchen")),
                kitchen.id, diagnostic = false, visible = true),
            Entry(Entity(thermostat.entity, "heat", JSONObject().put("friendly_name", "Kitchen")),
                kitchen.id, diagnostic = false, visible = true),
            Entry(Entity("light.spare", "on"), kitchen.id, diagnostic = false, visible = true),
        ), listOf(kitchen))
        val customTiles = listOf(light, player)
        val config = PanelConfig(roomTiles = mapOf(kitchen.id to customTiles))
        val state = PanelUi(layout = RoomLayout(config, catalogue))
        val tiles = state.roomSections.single().tiles
        assertEquals(listOf(light.entity, player.entity, thermostat.entity), tiles.map { it.entity })
        assertEquals(listOf("Kitchen", "Kitchen"), tiles.drop(1).map(state::label))

        val hidden = config.copy(roomTiles = mapOf(kitchen.id to (customTiles + thermostat.copy(hidden = true))))
        val heatingOff = config.copy(showRoomHeating = false)
        for (withoutThermostat in listOf(hidden, heatingOff)) {
            val section = RoomLayout(withoutThermostat, catalogue).sections.single()
            assertEquals(customTiles.map { it.entity }, section.tiles.map { it.entity })
        }
        val editedWithHeatingOff = RoomLayout(heatingOff, catalogue).controls(kitchen).map(RoomControl::preference)
        val heatingOnAgain = config.copy(roomTiles = mapOf(kitchen.id to editedWithHeatingOff))
        assertEquals(tiles, RoomLayout(heatingOnAgain, catalogue).sections.single().tiles)
    }

    @Test
    fun scenesCanBeAddedToARoomWithoutShowingOtherScenesByDefault() {
        val lounge = Area("lounge", "Lounge")
        val fireplace = Entity("scene.fireplace", "unknown")
        val movies = Entity("scene.movies", "2026-11-21T00:00:00+00:00")
        val config = PanelConfig(roomIds = listOf(lounge.id), roomTiles = mapOf(lounge.id to listOf(
            RoomTilePreference(movies.id, icon = TileIcon.MOVIE),
        )))
        val catalogue = Catalogue(listOf(fireplace, movies).map {
            Entry(it, lounge.id, diagnostic = false, visible = true)
        }, listOf(lounge))
        val restored = PanelConfigCodec.decode(PanelConfigCodec.encode(config))
        val layout = RoomLayout(restored, catalogue)
        assertEquals(listOf(TileSpec(movies.id, TileKind.ACTION, icon = TileIcon.MOVIE)), layout.sections.single().tiles)
        assertTrue(layout.controls(lounge).last().preference.hidden)
        assertTrue(fireplace.available)
    }

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
        val config = PanelConfig(roomIds = listOf(kitchen.id, hallway.id), roomTiles = mapOf(kitchen.id to listOf(
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
        assertEquals(listOf(TileKind.TOGGLE, TileKind.TOGGLE, TileKind.SLIDER), reset.map { it.kind })
        assertTrue(reset.none { it.wideOnTablet })
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

    @Test
    fun roomsStartWithAreasOfPinnedControlsAndPreserveAnExplicitSelection() {
        val kitchen = Area("kitchen", "Kitchen")
        val hall = Area("hall", "Hallway")
        val dining = Area("dining", "Dining room")
        val kitchenLight = Entity("light.island", "off")
        val catalogue = Catalogue(
            entries = listOf(
                Entry(kitchenLight, kitchen.id, diagnostic = false, visible = true),
                Entry(Entity("light.hall", "off"), hall.id, diagnostic = false, visible = true),
                Entry(Entity("light.dining", "off"), dining.id, diagnostic = false, visible = true),
            ),
            areas = listOf(hall, kitchen, dining),
        )
        val config = PanelConfig(favourites = listOf(TileSpec(kitchenLight.id, TileKind.TOGGLE)))
        assertEquals(listOf(kitchen), RoomLayout(config, catalogue).selectedRooms)
        assertTrue(RoomLayout(catalogue = catalogue).selectedRooms.isEmpty())
        assertEquals(listOf(dining, hall), RoomLayout(config.copy(roomIds = listOf(dining.id, hall.id)), catalogue).selectedRooms)
    }
}
