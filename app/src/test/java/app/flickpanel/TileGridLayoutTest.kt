package app.flickpanel

import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ui.dashboardTileColumns
import app.flickpanel.ui.homeTileGroups
import app.flickpanel.ui.packTileRows
import app.flickpanel.ui.roomTileColumns
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
    @Test
    fun homeKeepsCameraPairsSeparateFromTheAgenda() {
        val gate = TileSpec("camera.front_gate", TileKind.CAMERA)
        val front = TileSpec("camera.front_left", TileKind.CAMERA)
        val tasks = TileSpec("todo.shopping", TileKind.TASKS)
        val calendar = TileSpec("calendar.home", TileKind.CALENDAR)
        val person = TileSpec("person.sam", TileKind.PERSON)
        val app = TileSpec("app:example.media", TileKind.APP)
        val tiles = listOf(person, gate, tasks, front, app, calendar)
        val wide = homeTileGroups(tiles, columns = 4, compact = true, resizeTiles = true)
        assertEquals(listOf(listOf(gate, front)), wide.cameras)
        assertEquals(listOf(listOf(tasks, calendar)), wide.agenda)
        assertEquals(2, wide.agendaColumns)
        assertEquals(listOf(listOf(person)), wide.people)
        assertEquals(listOf(app), wide.shortcuts)

        val narrow = homeTileGroups(tiles, columns = 2, compact = false, resizeTiles = true)
        assertEquals(listOf(listOf(gate, front)), narrow.cameras)
        assertEquals(listOf(listOf(tasks, calendar)), narrow.agenda)
    }
    @Test
    fun standardGraphCanFillTheFourthShortcutSlot() {
        val apps = listOf("one", "two", "three").map { TileSpec("app:example.$it", TileKind.APP) }
        val graph = TileSpec("sensor.house_power", TileKind.TREND)
        val wideGraph = graph.copy(entity = "sensor.solar_power", wideOnTablet = true)
        val layout = homeTileGroups(listOf(wideGraph, graph) + apps, columns = 4, compact = true, resizeTiles = true)
        assertEquals(apps + graph, layout.shortcuts)
        assertEquals(listOf(listOf(wideGraph)), layout.history)
    }
    @Test
    fun roomGridUsesTheDetailsPaneWidthAndKeepsTilesReadable() {
        assertEquals(5, roomTileColumns(1168f, 1f))
        assertEquals(4, roomTileColumns(956f, 1f))
        assertEquals(3, roomTileColumns(768f, 1f))
        assertEquals(2, roomTileColumns(768f, 1.6f))
        assertEquals(2, roomTileColumns(600f, 1.6f))
        assertEquals(2, roomTileColumns(372f, 1f))
        assertEquals(1, roomTileColumns(360f, 2f))
        assertEquals(1, roomTileColumns(0f, 1f))
    }

    @Test
    fun homeShowsEveryPersonInOrdinaryRows() {
        val people = (1..6).map { TileSpec("person.profile_$it", TileKind.PERSON) }
        val wide = homeTileGroups(people, columns = 4, compact = true, resizeTiles = true)
        val narrow = homeTileGroups(people, columns = 1, compact = false, resizeTiles = true)
        assertEquals(2, wide.peopleColumns)
        assertEquals(people.chunked(2), wide.people)
        assertEquals(1, narrow.peopleColumns)
        assertEquals(people.chunked(1), narrow.people)
    }

    @Test
    fun peopleGridKeepsTheConfiguredWideCardAndSavedOrder() {
        val wide = TileSpec("person.profile_1", TileKind.PERSON, wideOnTablet = true)
        val others = (2..4).map { TileSpec("person.profile_$it", TileKind.PERSON) }
        assertEquals(listOf(listOf(wide), others.take(2), others.takeLast(1)),
            homeTileGroups(listOf(wide) + others, columns = 2, compact = false, resizeTiles = true).people)
    }
}
