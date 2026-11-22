package app.flickpanel

import app.flickpanel.config.CameraAction
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSelectionTest {
    @Test fun blankDisplayDropsRoomOnlyStatesAndKeepsSharedDependencies() {
        val kitchen = Area("kitchen", "Kitchen")
        val config = PanelConfig(
            favourites = listOf(
                TileSpec("light.island", TileKind.TOGGLE),
                TileSpec("script.hot_water", TileKind.ACTION, statusEntity = "timer.hot_water"),
                TileSpec("camera.door", TileKind.CAMERA, cameraStream = "camera.door_live",
                    cameraActions = listOf(CameraAction("light.porch"))),
                TileSpec("app:org.example.player", TileKind.APP),
            ),
            roomIds = listOf(kitchen.id),
            messageEntity = "sensor.home_message",
            screensaverMessageEntity = "sensor.screensaver_message",
            homeBadges = listOf(HomeBadge("binary_sensor.alert", null, listOf("on"))),
        )
        val catalogue = Catalogue(
            listOf(Entry(Entity("light.island", "on"), kitchen.id, false, true),
                Entry(Entity("light.sink", "off"), kitchen.id, false, true)),
            listOf(kitchen),
        )
        val dashboard = PanelUi(configured = true, layout = RoomLayout(config, catalogue))
        val shared = setOf("light.island", "script.hot_water", "timer.hot_water", "camera.door",
            "camera.door_live", "light.porch", "sensor.home_message", "sensor.screensaver_message",
            "binary_sensor.alert")

        assertEquals(shared + "light.sink", dashboard.liveEntityIds(roomsRequested = false))
        val blank = dashboard.copy(screenBlank = true)
        assertEquals(shared, blank.liveEntityIds(roomsRequested = true))
        assertEquals(shared + "light.sink", blank.wakeDisplay().liveEntityIds(roomsRequested = false))
        assertEquals(shared + "light.sink",
            blank.copy(pending = mapOf("light.sink" to "Sending")).liveEntityIds(roomsRequested = true))
    }
}
