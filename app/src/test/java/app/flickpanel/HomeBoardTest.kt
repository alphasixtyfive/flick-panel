package app.flickpanel

import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ui.homeBands
import app.flickpanel.ui.homePages
import app.flickpanel.ui.homePagerHeight
import app.flickpanel.ui.homeTileGroups
import app.flickpanel.ui.tileGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoardTest {
    private val tiles = listOf(
        TileSpec("light.wall", TileKind.TOGGLE),
        TileSpec("light.island", TileKind.TOGGLE),
        TileSpec("button.boost", TileKind.ACTION),
        TileSpec("media_player.kitchen", TileKind.MEDIA),
        TileSpec("cover.front", TileKind.COVER),
        TileSpec("cover.back", TileKind.COVER),
        TileSpec("camera.front", TileKind.CAMERA),
        TileSpec("camera.side", TileKind.CAMERA),
        TileSpec("todo.shopping_list", TileKind.TASKS),
        TileSpec("calendar.home", TileKind.CALENDAR),
        TileSpec("sensor.house_power", TileKind.TREND),
    ) + (1..3).map { TileSpec("app:example.shortcut$it", TileKind.APP) } +
        (1..6).map { TileSpec("person.profile_$it", TileKind.PERSON) }

    @Test
    fun landscapeTabletFitsTheKitchenLayoutOnOnePage() {
        val groups = homeTileGroups(tiles, 4, compact = true, resizeTiles = true)
        val gates = setOf("cover.front", "cover.back")
        val page = homePages(homeBands(groups, 1f, 620.dp, gateIds = gates), 620.dp).single()
        assertEquals(4, page.first().lanes.single().single().tiles.size)
        assertEquals(128.dp, page.first().height)
        assertEquals(2, page[1].lanes.size)
        val peopleLane = page[1].lanes.last()
        assertEquals(gates, peopleLane.first().tiles.map(TileSpec::entity).toSet())
        assertTrue(peopleLane.drop(1).all { it.height < 86.dp })
        val shown = page.flatMap { it.lanes.flatten().flatMap { row -> row.tiles } }
        assertEquals(tiles.size, shown.size)
        assertEquals(tiles.toSet(), shown.toSet())
        assertEquals(1, homePages(homeBands(groups, 1f, 573.dp, gateIds = gates), 573.dp).size)
    }

    @Test
    fun shortLandscapeShowsTheFourPrimaryKitchenControlsOnItsFirstPage() {
        val height = 249.dp
        val groups = homeTileGroups(tiles, 2, compact = false, resizeTiles = true, inlineGraph = true)
        val pages = homePages(homeBands(groups, 1f, height), height)
        assertEquals(7, pages.size)
        assertEquals(tiles.take(4), pages.first().flatMap { it.lanes.flatten().flatMap { row -> row.tiles } })
        assertEquals(TileKind.TREND, groups.shortcuts.last().kind)
    }

    @Test
    fun tinyDashboardKeepsAllTilesOnOneScrollSurfaceWithoutASeparatePager() {
        val groups = homeTileGroups(tiles, 2, compact = false, resizeTiles = true, inlineGraph = true)
        val height = 108.dp
        val bands = homeBands(groups, 1f, height)
        assertTrue(bands.first().height <= height)
        assertEquals(bands, homePages(bands, height).single())
    }

    @Test
    fun narrowWindowsReserveSpaceForPageButtonsAndKeepEveryTile() {
        val groups = homeTileGroups(tiles, 2, compact = false, resizeTiles = true)
        val height = 400.dp
        val pages = homePages(homeBands(groups, 1.3f, height), height)
        assertTrue(pages.size > 1)
        for (page in pages) {
            val used = page.fold(tileGap * (page.size - 1)) { sum, band -> sum + band.height }
            assertTrue(used + homePagerHeight <= height)
        }
        val shown = pages.flatten().flatMap { it.lanes.flatten().flatMap { row -> row.tiles } }
        assertEquals(tiles.size, shown.size)
        assertEquals(tiles.toSet(), shown.toSet())
    }
}
