package app.flickpanel

import app.flickpanel.config.PanelBackup
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelStoreProblem
import app.flickpanel.config.PanelTheme
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileAccent
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.PhotoFolder
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelBackupTest {
    @Test
    fun preservesRoomSelectionTilesThemeAndDisplayPreferences() {
        val config = PanelConfig(
            favourites = listOf(TileSpec("light.wall", TileKind.TOGGLE, accent = TileAccent.AMBER)),
            theme = PanelTheme.AMBER,
            roomIds = listOf("kitchen"),
            roomTiles = mapOf("kitchen" to listOf(
                RoomTilePreference("media_player.kitchen", label = "Kitchen speaker",
                    accent = TileAccent.TEAL, followsArea = true),
            )),
            idleAlbum = PhotoFolder("media-source://media_source/media/local/photos", "Family photos"),
            dashboardBrightness = 100,
            screensaverBrightness = 8,
        )
        val output = ByteArrayOutputStream()
        PanelBackup.write(config, output)
        assertEquals(config, PanelBackup.read(output.toByteArray().inputStream()))
    }

    @Test(expected = PanelStoreProblem::class)
    fun rejectsInvalidLayoutBeforeReturningAnySettings() {
        val file = """{"format":"flick-panel","version":1,"layout":{"version":1,"tiles":[{}]}}"""
        PanelBackup.read(file.byteInputStream())
    }

    @Test(expected = PanelStoreProblem::class)
    fun rejectsOversizedInput() {
        PanelBackup.read(ByteArray(513 * 1024).inputStream())
    }
}
