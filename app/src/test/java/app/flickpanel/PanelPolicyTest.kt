package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.PanelTheme
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.validPin
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entry
import app.flickpanel.ha.Entity
import app.flickpanel.ha.PhotoFolder
import app.flickpanel.ha.parseServer
import app.flickpanel.ha.reduceEntities
import app.flickpanel.ha.sameOrigin
import app.flickpanel.ha.weather
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPolicyTest {
    @Test fun configurationRoundTripsWithoutCredentials() {
        val original = PanelConfig(
            favourites = listOf(
                TileSpec("sensor.kitchen_temperature", TileKind.TREND),
                TileSpec("input_button.boost", TileKind.ACTION, "Boost", "timer.hot_water_boost")
            ),
            theme = PanelTheme.AMBER,
            messageEntity = "input_text.panel_notice",
            presenceEntity = "binary_sensor.near_panel",
            idleSeconds = 120, wallMode = false,
            idleAlbum = PhotoFolder("media-source://synology_dsm/nas/album", "TV Gallery")
        )
        val json = PanelConfigCodec.encode(original)
        assertEquals(original, PanelConfigCodec.decode(json))
        assertFalse(json.contains("pin"))
        assertFalse(json.contains("token"))
    }

    @Test fun configurationFiltersInvalidOrDuplicateTiles() {
        val parsed = PanelConfigCodec.decode("""{"version":1,"idle":3000,"tiles":[
          {"entity":"sensor.temperature","kind":"VALUE","status":"timer.fake"},
          {"entity":"sensor.temperature","kind":"TREND"},
          {"entity":"../x","kind":"VALUE"},
          {"entity":"light.lamp","kind":"unknown"}],
          "presence":"switch.lights","theme":"unsupported"}""")
        assertEquals(listOf(TileSpec("sensor.temperature", TileKind.VALUE)), parsed.favourites)
        assertEquals(900, parsed.idleSeconds)
        assertEquals(PanelTheme.SLATE, parsed.theme)
        assertNull(parsed.presenceEntity)
    }

    @Test fun pinUsesOnlyAsciiDigitsAndPreservesLeadingZeros() {
        assertTrue(validPin("0012"))
        assertTrue(validPin("01234567"))
        assertFalse(validPin("123"))
        assertFalse(validPin("１２３４"))
        assertFalse(validPin(" 1234"))
    }

    @Test fun serverUsesHttpsOrAnExplicitPrivateAddress() {
        assertEquals("https://home.example/ha/", parseServer("https://home.example/ha").toString())
        assertEquals("http://192.168.1.5:8123/", parseServer("http://192.168.1.5:8123").toString())
        assertTrue(runCatching { parseServer("http://home.example") }.isFailure)
        assertTrue(runCatching { parseServer("http://172.32.1.1") }.isFailure)
        assertTrue(runCatching { parseServer("https://user:pass@home.example") }.isFailure)
        assertTrue(runCatching { parseServer("https://home.example?a=1") }.isFailure)
        assertFalse(parseServer("https://home.example").sameOrigin("https://home.example:8123/hls/a".toHttpUrl()))
    }

    @Test fun dimmerAvailabilityComesFromCapabilities() {
        val binary = Entity("light.plug", "off", JSONObject("""{"supported_color_modes":["onoff"]}"""))
        val dimmer = binary.copy(attributes = JSONObject("""{"supported_color_modes":["color_temp"]}"""))
        assertEquals(listOf(TileKind.TOGGLE), binary.kinds())
        assertEquals(listOf(TileKind.SLIDER, TileKind.TOGGLE), dimmer.kinds())
        assertEquals(0, dimmer.brightness)
        assertEquals(emptyList<TileKind>(), Entity("lock.door", "locked").kinds())
    }

    @Test fun weatherUsesHomeAssistantConditionsAndUnits() {
        val entity = Entity("weather.barnet", "partlycloudy",
            JSONObject("""{"temperature":5.9,"temperature_unit":"°C"}"""))
        assertEquals(listOf(TileKind.WEATHER), entity.kinds())
        assertEquals("Partly cloudy", entity.weather()?.condition)
        assertEquals(5.9, entity.weather()?.temperature)
        assertEquals("°C", entity.weather()?.unit)
        assertNull(entity.copy(state = "unavailable").weather())
    }

    @Test fun camerasCanStayInTheGalleryWithoutOccupyingHome() {
        val original = PanelConfig(favourites = listOf(TileSpec("camera.driveway", TileKind.CAMERA, showOnHome = false)))
        assertEquals(original, PanelConfigCodec.decode(PanelConfigCodec.encode(original)))
        val legacy = """{"version":1,"tiles":[{"entity":"camera.front_door","kind":"CAMERA"}]}"""
        assertTrue(PanelConfigCodec.decode(legacy).favourites.single().showOnHome)
    }

    @Test fun shortcutsRoundTripAsLocalPackages() {
        val shortcut = TileSpec("app:org.example.player", TileKind.APP, "Player")
        val config = PanelConfig(favourites = listOf(shortcut, TileSpec("https://example.org", TileKind.APP)))
        val saved = PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites.single()
        assertEquals(shortcut, saved)
        assertEquals("org.example.player", saved.appPackage)
    }

    @Test fun roomsKeepOnlyVisibleHouseholdControlsInTheirOwnArea() {
        val kitchen = Area("area_1", "Kitchen")
        val hall = Area("area_2", "Hall")
        val light = Entity("light.pendant", "on")
        val heat = Entity("climate.kitchen", "heat")
        val catalogue = Catalogue(listOf(
            Entry(heat, kitchen.id, diagnostic = false, visible = true),
            Entry(Entity("light.status", "off"), kitchen.id, diagnostic = true, visible = true),
            Entry(Entity("light.hidden", "off"), hall.id, diagnostic = false, visible = false),
            Entry(light, kitchen.id, diagnostic = false, visible = true),
            Entry(Entity("switch.hall", "off"), hall.id, diagnostic = false, visible = true)
        ), listOf(hall, kitchen))
        assertEquals(listOf(kitchen), catalogue.rooms)
        assertEquals(listOf(light, heat), catalogue.room(kitchen.id).map { it.entity })
        assertEquals(light, catalogue.entity(light.id))
        assertTrue(catalogue.room(hall.id).isEmpty())
    }

    @Test fun roomTilesUseShortNamesAndKeepAnExplicitBrightnessControl() {
        val kitchen = Area("area_1", "Kitchen")
        val dimmer = Entity("light.pendant", "off", JSONObject("""{
            "friendly_name":"Kitchen pendant","supported_color_modes":["brightness"]
        }"""))
        val catalogue = Catalogue(listOf(Entry(dimmer, kitchen.id, diagnostic = false, visible = true)), listOf(kitchen))
        val state = RoomLayout(catalogue = catalogue)
        assertEquals(TileSpec(dimmer.id, TileKind.TOGGLE, "Pendant"), state.sections.single().tiles.single())
        val pinned = state.copy(config = PanelConfig(favourites = listOf(TileSpec(dimmer.id, TileKind.SLIDER, "Main light"))))
        assertEquals(TileSpec(dimmer.id, TileKind.SLIDER, "Main light"), pinned.sections.single().tiles.single())
    }

    @Test fun enabledRoomsKeepTheirConfiguredOrder() {
        val kitchen = Area("kitchen", "Kitchen")
        val dining = Area("dining_room", "Dining room")
        val catalogue = Catalogue(listOf(
            Entry(Entity("light.kitchen", "on"), kitchen.id, diagnostic = false, visible = true),
            Entry(Entity("light.dining", "off"), dining.id, diagnostic = false, visible = true)
        ), listOf(kitchen, dining))
        val config = PanelConfig(roomIds = listOf(dining.id, "missing_area", kitchen.id))
        val loaded = PanelConfigCodec.decode(PanelConfigCodec.encode(config))
        assertEquals(config, loaded)
        assertEquals(listOf(dining, kitchen), RoomLayout(loaded, catalogue).sections.map(RoomSection::area))
        val empty = PanelConfigCodec.decode(PanelConfigCodec.encode(PanelConfig(roomIds = emptyList())))
        assertTrue(RoomLayout(empty, catalogue).sections.isEmpty())
    }

    @Test fun compactEventPatchesStateAndAttributesWithoutMutatingPreviousState() {
        val ids = setOf("light.lamp", "sensor.p")
        val snapshot = reduceEntities(emptyMap(),
            JSONObject("""{"a":{"light.lamp":{"s":"on","a":{"brightness":128,"friendly_name":"Lamp"}},
                "sensor.p":{"s":"12"},"sensor.extra":{"s":"99"}}}"""), ids, true)
        val current = reduceEntities(snapshot,
            JSONObject("""{"c":{"light.lamp":{"+":{"s":"off","a":{"friendly_name":"Lounge lamp"}},
                "-":{"a":["brightness"]}}},"r":["sensor.p"]}"""), ids, false)
        assertEquals("on", snapshot.getValue("light.lamp").state)
        assertEquals(128, snapshot.getValue("light.lamp").attributes.optInt("brightness"))
        assertEquals(setOf("light.lamp"), current.keys)
        assertEquals("off", current.getValue("light.lamp").state)
        assertEquals("Lounge lamp", current.getValue("light.lamp").name)
        assertFalse(current.getValue("light.lamp").attributes.has("brightness"))
        assertTrue(reduceEntities(current, JSONObject("""{"a":{}}"""), ids, true).isEmpty())
    }
}
