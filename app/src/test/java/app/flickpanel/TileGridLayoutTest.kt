package app.flickpanel

import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ui.dashboardTileColumns
import app.flickpanel.ui.packTileRows
import app.flickpanel.ui.tileSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class TileGridLayoutTest {
    @Test
    fun columnsLeaveRoomForControlsAtDifferentWindowWidthsAndTextSizes() {
        assertEquals(1, dashboardTileColumns(320f, 1f))
        assertEquals(2, dashboardTileColumns(412f, 1f))
        assertEquals(1, dashboardTileColumns(412f, 1.6f))
        assertEquals(2, dashboardTileColumns(768f, 1f))
        assertEquals(4, dashboardTileColumns(840f, 1f))
        assertEquals(2, dashboardTileColumns(840f, 1.6f))
        assertEquals(4, dashboardTileColumns(1280f, 1.6f))
        assertEquals(2, dashboardTileColumns(1280f, 2f))
        assertEquals(1, dashboardTileColumns(600f, 2f))
    }

    @Test
    fun widerTilesPackInSavedOrderAndClampToASingleColumn() {
        val light = TileSpec("light.kitchen", TileKind.TOGGLE)
        val wide = TileSpec("sensor.power", TileKind.VALUE, wideOnTablet = true)
        val camera = TileSpec("camera.front", TileKind.CAMERA)
        val tiles = listOf(light, wide, camera)
        assertEquals(listOf(listOf(light, wide), listOf(camera)),
            packTileRows(tiles, columns = 4, compact = true, resizeTiles = true))
        assertEquals(tiles.map { listOf(it) },
            packTileRows(tiles, columns = 1, compact = false, resizeTiles = true))
        assertEquals(listOf(listOf(light, wide), listOf(camera)),
            packTileRows(tiles, columns = 2, compact = false, resizeTiles = false))
    }

    @Test
    fun widePreferenceAppliesOnMediumWindowsAndFixedTilesKeepTheirWidth() {
        val value = TileSpec("sensor.power", TileKind.VALUE)
        val shortcut = TileSpec("app:org.example.player", TileKind.APP, wideOnTablet = true)
        assertEquals(1, tileSpan(value, columns = 2, compact = false, resizeTiles = true))
        assertEquals(2, tileSpan(value.copy(wideOnTablet = true), columns = 2, compact = false, resizeTiles = true))
        assertEquals(1, tileSpan(shortcut, columns = 4, compact = true, resizeTiles = true))
        val climate = TileSpec("climate.lounge", TileKind.CLIMATE)
        assertEquals(1, tileSpan(climate, columns = 2, compact = false, resizeTiles = true))
        assertEquals(2, tileSpan(climate.copy(wideOnTablet = true), columns = 2, compact = false, resizeTiles = true))
        assertEquals(2, tileSpan(climate, columns = 2, compact = false, resizeTiles = false))
        for (agenda in listOf(TileSpec("todo.groceries", TileKind.TASKS), TileSpec("calendar.home", TileKind.CALENDAR))) {
            assertEquals(1, tileSpan(agenda, columns = 4, compact = true, resizeTiles = true))
            assertEquals(2, tileSpan(agenda.copy(wideOnTablet = true), columns = 4, compact = true, resizeTiles = true))
        }
    }
}
