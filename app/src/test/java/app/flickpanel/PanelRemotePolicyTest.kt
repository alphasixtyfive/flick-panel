package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelRemotePolicyTest {
    private val linked = PanelUi(configured = true, link = Link.ONLINE, remoteName = "Kitchen panel",
        layout = RoomLayout(PanelConfig(favourites = listOf(TileSpec("camera.gate", TileKind.CAMERA, showOnHome = false)))))

    @Test
    fun blankingClosesMediaAndWakeReturnsToHome() {
        val gallery = linked.copy(page = Page.CAMERAS).showCamera("camera.gate", 99)
        val blank = gallery.remoteCommand(PanelCommand.Sleep, 100)
        assertTrue(blank.screenBlank)
        assertNull(blank.camera)
        val awake = blank.remoteCommand(PanelCommand.Wake, 101)
        assertFalse(awake.screenBlank)
        assertEquals(Page.FAVOURITES, awake.page)
    }

    @Test
    fun openingAnotherViewerReplacesTheCameraAndClearsItsOpeningTime() {
        val music = TileSpec("media_player.kitchen", TileKind.MEDIA)
        val gallery = linked.copy(layout = RoomLayout(linked.config.copy(favourites = linked.config.favourites + music)))
            .showCamera("camera.gate", 10)
        val player = gallery.remoteCommand(PanelCommand.OpenTile(music.entity), 11)
        assertEquals(PanelViewer.Music(music.entity), player.viewer)
        assertNull(player.camera)
        assertEquals(0L, player.cameraOpenedAt)
        assertFalse(player.showingDashboard)
        assertTrue(player.remoteCommand(PanelCommand.CloseViewer, 12).showingDashboard)
    }

    @Test
    fun onlyConfiguredCamerasCanWakeTheDisplay() {
        val blank = linked.copy(screenBlank = true)
        assertSame(blank, blank.remoteCommand(PanelCommand.OpenCamera("camera.private"), 100))
        val opened = blank.remoteCommand(PanelCommand.OpenCamera("camera.gate"), 101)
        assertFalse(opened.screenBlank)
        assertEquals("camera.gate", opened.camera)
        assertEquals(101L, opened.cameraOpenedAt)
    }

    @Test
    fun remoteCommandsLeaveSetupAndAnUnlinkedPanelAlone() {
        for (state in listOf(linked.copy(editor = true), linked.copy(unlocking = true),
            linked.copy(remoteName = null), linked.copy(link = Link.OFFLINE))) {
            assertSame(state, state.remoteCommand(PanelCommand.Sleep, 100))
        }
    }

    @Test
    fun scopedRemoteViewsNeverRunActionsOrTouchSavedSettings() {
        val list = TileSpec("todo.shopping_list", TileKind.TASKS)
        val lights = TileSpec("light.kitchen", TileKind.TOGGLE)
        val panel = linked.copy(layout = RoomLayout(PanelConfig(favourites = listOf(list, lights))))
        val asleep = panel.remoteCommand(PanelCommand.Sleep, 0)
        assertSame(asleep, asleep.remoteCommand(PanelCommand.OpenTile(lights.entity), 1))
        val agenda = asleep.remoteCommand(PanelCommand.OpenTile(list.entity), 2)
        assertFalse(agenda.screenBlank)
        assertEquals(list.entity, agenda.agendaOpen)
        val clock = agenda.remoteCommand(PanelCommand.ShowClock, 3)
        assertTrue(clock.clock)
        assertNull(clock.agendaOpen)
        val rooms = clock.remoteCommand(PanelCommand.ShowPage(Page.ROOMS), 4)
        assertFalse(rooms.clock)
        assertEquals(Page.ROOMS, rooms.page)
        val overridden = rooms.remoteCommand(PanelCommand.SetBrightness(60), 5)
            .remoteCommand(PanelCommand.KeepAwake(!panel.config.wallMode), 6)
        assertEquals(60, overridden.brightnessPercent)
        assertEquals(!panel.config.wallMode, overridden.keepsDisplayAwake)
        assertEquals(panel.config, overridden.config)
        assertEquals(panel.config.wallMode, overridden.remoteCommand(PanelCommand.KeepAwake(null), 7).keepsDisplayAwake)
        assertNull(overridden.remoteCommand(PanelCommand.SetBrightness(null), 8).brightnessPercent)
    }

    @Test
    fun localWakeReturnsEitherIdleDisplayToFavouritesWhileOffline() {
        val panel = linked.copy(remoteName = null, link = Link.OFFLINE, page = Page.ROOMS)
        for (idle in listOf(panel.copy(clock = true), panel.copy(screenBlank = true))) {
            val awake = idle.wakeDisplay()
            assertFalse(awake.clock)
            assertFalse(awake.screenBlank)
            assertEquals(Page.FAVOURITES, awake.page)
        }
    }

    @Test
    fun automationCanWakeTheClockToFavourites() {
        val clock = linked.copy(page = Page.ROOMS, clock = true)
        val awake = clock.remoteCommand(PanelCommand.Wake, openedAt = 100)

        assertFalse(awake.clock)
        assertEquals(Page.FAVOURITES, awake.page)
    }
}
