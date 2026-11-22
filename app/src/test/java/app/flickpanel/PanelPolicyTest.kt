package app.flickpanel

import app.flickpanel.config.GraphColour
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.PanelTheme
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.validPin
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entry
import app.flickpanel.ha.Entity
import app.flickpanel.ha.PhotoFolder
import app.flickpanel.ha.adjacentTemperature
import app.flickpanel.ha.parseServer
import app.flickpanel.ha.reduceEntities
import app.flickpanel.ha.sameOrigin
import app.flickpanel.ha.weather
import app.flickpanel.ui.timerSecondsRemaining
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPolicyTest {
    @Test fun graphColoursRoundTripAndOlderLayoutsUseTheTheme() {
        val graph = TileSpec("sensor.power", TileKind.TREND, graphColour = GraphColour.AMBER)
        val value = TileSpec("sensor.temperature", TileKind.VALUE)
        val saved = PanelConfigCodec.encode(PanelConfig(favourites = listOf(graph, value)))
        assertEquals(listOf(graph, value), PanelConfigCodec.decode(saved).favourites)

        val oldTile = """{"version":1,"tiles":[{"entity":"sensor.power","kind":"TREND"}]}"""
        assertEquals(graph.copy(graphColour = null), PanelConfigCodec.decode(oldTile).favourites.single())
        val unknownColour = saved.replace("AMBER", "UNRECOGNISED")
        assertEquals(listOf(graph.copy(graphColour = null), value), PanelConfigCodec.decode(unknownColour).favourites)
    }

    @Test fun temperatureStepsRespectDeviceLimitsAndAvailability() {
        val attributes = JSONObject().put("temperature", 22.0).put("min_temp", 15.0)
            .put("max_temp", 22.0).put("target_temp_step", .5)
        val thermostat = Entity("climate.kitchen", "heat", attributes)

        assertEquals(21.5, thermostat.adjacentTemperature(increase = false))
        assertNull(thermostat.adjacentTemperature(increase = true))
        assertNull(thermostat.copy(state = "unavailable").adjacentTemperature(increase = false))
        assertNull(thermostat.copy(attributes = JSONObject()).adjacentTemperature(increase = true))
    }

    @Test fun configurationRoundTripsWithoutCredentials() {
        val original = PanelConfig(
            favourites = listOf(
                TileSpec("sensor.kitchen_temperature", TileKind.TREND),
                TileSpec("input_button.boost", TileKind.ACTION, "Boost", "timer.hot_water_boost")
            ),
            theme = PanelTheme.AMBER,
            messageEntity = "input_text.panel_notice",
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
          "theme":"unsupported"}""")
        assertEquals(listOf(TileSpec("sensor.temperature", TileKind.VALUE)), parsed.favourites)
        assertEquals(900, parsed.idleSeconds)
        assertEquals(PanelTheme.SLATE, parsed.theme)
    }

    @Test fun configurationIgnoresTheRetiredWakeSetting() {
        val config = PanelConfig(messageEntity = "input_text.panel_notice")
        val saved = JSONObject(PanelConfigCodec.encode(config))
            .put("presence", "binary_sensor.near_panel").toString()
        val restored = PanelConfigCodec.decode(saved)

        assertEquals(config, restored)
        assertFalse(JSONObject(PanelConfigCodec.encode(restored)).has("presence"))
    }

    @Test fun configurationKeepsEverySavedCameraAndRoom() {
        val config = PanelConfig(
            favourites = List(60) { TileSpec("camera.feed_$it", TileKind.CAMERA, showOnHome = false) },
            roomIds = List(60) { "area_$it" },
        )
        assertEquals(config, PanelConfigCodec.decode(PanelConfigCodec.encode(config)))
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

    @Test fun cameraGateLinksRequireAnOpenableGateCover() {
        val gateId = "cover.front_gate"
        val gate = Entity(gateId, "closed", JSONObject("""{"device_class":"gate","supported_features":1}"""))
        assertTrue(gate.gateWithOpenControl)
        assertFalse(gate.copy(attributes = JSONObject("""{"device_class":"garage","supported_features":1}""")).gateWithOpenControl)
        assertFalse(gate.copy(attributes = JSONObject("""{"device_class":"gate","supported_features":2}""")).gateWithOpenControl)
        val camera = TileSpec("camera.driveway", TileKind.CAMERA, cameraGate = gateId)
        val sensor = TileSpec("sensor.power", TileKind.VALUE, cameraGate = gateId)
        val config = PanelConfig(favourites = listOf(camera, sensor))
        assertEquals(listOf(camera, sensor.copy(cameraGate = null)),
            PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites)
    }

    @Test fun cameraCanUseAnAlternateLiveStreamWithoutChangingItsTile() {
        val camera = TileSpec("camera.gate", TileKind.CAMERA, cameraGate = "cover.gate", cameraStream = "camera.gate_low")
        val invalid = TileSpec("camera.side", TileKind.CAMERA, cameraStream = "sensor.side")
        val config = PanelConfig(favourites = listOf(camera, invalid))
        assertEquals(listOf(camera, invalid.copy(cameraStream = null)),
            PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites)
    }

    @Test fun layoutPreservesAppearanceAndActionConfirmation() {
        val action = TileSpec("script.night_mode", TileKind.ACTION, confirmAction = true)
        val value = TileSpec("sensor.power", TileKind.VALUE, confirmAction = true)
        val config = PanelConfig(favourites = listOf(action, value), idleSeconds = 0,
            highContrast = true, reduceMotion = true)
        assertEquals(config.copy(favourites = listOf(action, value.copy(confirmAction = false))),
            PanelConfigCodec.decode(PanelConfigCodec.encode(config)))
        val legacy = PanelConfigCodec.decode("""{"version":1,"tiles":[{"entity":"scene.home","kind":"ACTION"}]}""")
        assertFalse(legacy.favourites.single().confirmAction)
        assertFalse(legacy.reduceMotion)
        assertFalse(legacy.highContrast)
    }

    @Test fun numberAndSelectorControlsUseHomeAssistantCapabilities() {
        val setpoint = Entity("input_number.target", "0.2", JSONObject("""{"min":0,"max":0.3,"step":0.1}"""))
        assertEquals(listOf(TileKind.NUMBER, TileKind.VALUE), setpoint.kinds())
        val range = checkNotNull(setpoint.numberRange)
        assertEquals("0.3", range.adjacent(checkNotNull(setpoint.numberValue), true)?.toPlainString())
        assertEquals("0.2", range.adjacent("0.25".toBigDecimal(), false)?.toPlainString())
        assertNull(range.adjacent("0.3".toBigDecimal(), true))
        assertEquals(listOf(TileKind.VALUE), setpoint.copy(attributes = JSONObject()).kinds())
        val input = Entity("input_select.mode", "Home", JSONObject("""{"options":["Home","Quiet","Home",4,""]}"""))
        assertEquals(listOf("Home", "Quiet"), input.options)
        assertEquals(listOf(TileKind.CHOICE, TileKind.VALUE), input.kinds())
        assertEquals(listOf(TileKind.VALUE), Entity("binary_sensor.leak", "off").kinds())
    }

    @Test fun agendaTilesRoundTripAndRequireTheirHomeAssistantDomains() {
        val tasks = TileSpec("todo.shopping", TileKind.TASKS, wideOnTablet = true)
        val calendar = TileSpec("calendar.family", TileKind.CALENDAR)
        val invalid = TileSpec("sensor.power", TileKind.CALENDAR)
        val config = PanelConfig(favourites = listOf(tasks, calendar, invalid))
        assertEquals(listOf(tasks, calendar), PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites)
        assertEquals(listOf(TileKind.TASKS), Entity(tasks.entity, "4").kinds())
        assertEquals(listOf(TileKind.CALENDAR), Entity(calendar.entity, "off").kinds())
    }

    @Test fun readerLayoutsKeepTheirExplicitTextSource() {
        val recipe = TileSpec("sensor.recipe", TileKind.READER, readerAttribute = "recipe_text")
        val person = TileSpec("person.resident", TileKind.PERSON)
        val invalid = TileSpec("sensor.private", TileKind.READER, readerAttribute = "access_token")
        val config = PanelConfig(favourites = listOf(recipe, person, invalid))
        assertEquals(listOf(recipe, person), PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites)
    }

    @Test fun countdownKeepsTheFinalSecondVisibleUntilItExpires() {
        assertEquals(listOf(2L, 1L, 0L, 0L), listOf(3_999L, 4_999L, 5_000L, 6_000L).map {
            timerSecondsRemaining(deadline = 5_000L, now = it)
        })
        assertEquals(listOf(TileKind.TIMER, TileKind.VALUE), Entity("timer.boost", "active").kinds())
    }

    @Test fun shortcutsRoundTripAsLocalPackages() {
        val shortcut = TileSpec("app:org.example.player", TileKind.APP, "Player")
        val config = PanelConfig(favourites = listOf(shortcut, TileSpec("https://example.org", TileKind.APP)))
        val saved = PanelConfigCodec.decode(PanelConfigCodec.encode(config)).favourites.single()
        assertEquals(shortcut, saved)
        assertEquals("org.example.player", saved.appPackage)
    }

    @Test fun roomCandidatesKeepVisibleHouseholdControlsInTheirOwnArea() {
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
        assertEquals(listOf(hall, kitchen), catalogue.rooms)
        assertEquals(setOf(light, heat), catalogue.room(kitchen.id).map { it.entity }.toSet())
        assertEquals(light, catalogue.entity(light.id))
        assertEquals("switch.hall", catalogue.room(hall.id).single().entity.id)
        assertTrue(RoomLayout(PanelConfig(roomIds = listOf(hall.id)), catalogue).sections.isEmpty())
    }

    @Test fun roomTilesShortenTheAreaNameAndRespectTheSwitchSetting() {
        val kitchen = Area("area_1", "Kitchen")
        val dimmer = Entity("light.pendant", "off", JSONObject("""{
            "friendly_name":"Kitchen pendant","supported_color_modes":["brightness"]
        }"""))
        val catalogue = Catalogue(listOf(Entry(dimmer, kitchen.id, diagnostic = false, visible = true)), listOf(kitchen))
        val config = PanelConfig(roomIds = listOf(kitchen.id))
        val automatic = RoomLayout(config, catalogue).sections.single().tiles.single()
        assertEquals(TileSpec(dimmer.id, TileKind.SLIDER), automatic)
        val favourite = TileSpec(dimmer.id, TileKind.SLIDER, "Main light")
        val switched = config.copy(favourites = listOf(favourite),
            roomTiles = mapOf(kitchen.id to listOf(RoomTilePreference(dimmer.id, TileKind.TOGGLE))))
        val state = PanelUi(layout = RoomLayout(switched, catalogue))
        val tile = state.roomSections.single().tiles.single()
        assertEquals(TileKind.TOGGLE, tile.kind)
        assertEquals("Pendant", kitchen.tileName(state.label(tile)))
        assertEquals("Main light", state.label(favourite))
        assertEquals("Wall lights", kitchen.tileName("KITCHEN: Wall lights"))
        assertEquals("Kitchen", kitchen.tileName("Kitchen"))
        assertEquals("Kitchenette", kitchen.tileName("Kitchenette"))
        assertEquals("Hallway lights", kitchen.tileName("Hallway lights"))
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

    @Test fun compactEventsWithOnlyTimestampsOrRepeatedValuesReuseTheSnapshot() {
        val lamp = Entity("light.kitchen", "on", JSONObject().put("brightness", 128))
        val power = Entity("sensor.power", "380")
        val current = mapOf(lamp.id to lamp, power.id to power)
        val noChange = JSONObject("""{"c":{"light.kitchen":{"+":{"s":"on","a":{"brightness":128},"t":10},
            "-":{"a":["already_missing"]}},"sensor.power":{"+":{"t":10}}}}""")
        assertSame(current, reduceEntities(current, noChange, current.keys, initial = false))

        val pruned = reduceEntities(current, noChange, setOf(lamp.id), initial = false)
        assertEquals(setOf(lamp.id), pruned.keys)
        assertSame(lamp, pruned[lamp.id])
        assertEquals(setOf(lamp.id, power.id), current.keys)
    }
}
