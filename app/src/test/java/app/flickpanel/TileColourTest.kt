package app.flickpanel

import androidx.compose.ui.graphics.Color
import app.flickpanel.config.TileAccent
import app.flickpanel.ui.accentInk
import app.flickpanel.ui.contrastRatio
import app.flickpanel.ui.tileIconColours
import app.flickpanel.ui.tonedTileContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileColourTest {
    @Test
    fun accentContainersPreserveTextContrastInLightAndDarkThemes() {
        val schemes = listOf(
            Triple(Color(0xff242b35), Color(0xfffbfcfd), Color(0xffe4ecf6)),
            Triple(Color(0xff332d26), Color(0xfff5eee8), Color(0xffcfc1b3)),
            Triple(Color(0xffe6eaf0), Color(0xff202832), Color(0xff364351)),
        )
        for ((surface, label, secondary) in schemes) {
            for (accent in TileAccent.entries) {
                val resting = tonedTileContainer(accent, surface, label, secondary, active = false)
                val active = tonedTileContainer(accent, surface, label, secondary, active = true)
                assertNotEquals(surface, resting)
                assertNotEquals(resting, active)
                assertTrue("Active $accent has more emphasis",
                    contrastRatio(active, surface) > contrastRatio(resting, surface))
                val icon = accentInk(accent, resting, label, filledSurface = active)
                for (container in listOf(resting, active)) {
                    assertTrue("$accent icon contrast", contrastRatio(icon, container) >= 3f)
                    assertTrue("$accent label contrast", contrastRatio(label, container) >= 4.5f)
                    assertTrue("$accent status contrast", contrastRatio(secondary, container) >= 4.5f)
                    val badge = tileIconColours(container, icon)
                    assertEquals(icon, badge.ink)
                    assertTrue("$accent badge icon contrast", contrastRatio(badge.ink, badge.circle) >= 3f)
                    assertTrue("$accent badge tonal separation", contrastRatio(badge.circle, container) in 1.01f..1.4f)
                }
            }
        }
    }

    @Test
    fun badgeShadesAwayFromAnAccentNearTheContrastLimit() {
        val surface = Color(0xff444444)
        val accent = Color(0xff949494)
        val badge = tileIconColours(surface, accent)
        assertEquals(accent, badge.ink)
        assertTrue(badge.circle.red < surface.red)
        assertTrue(contrastRatio(badge.ink, badge.circle) >= 3f)
    }

    @Test
    fun badgeFindsReadableInkWhenThemeAccentMatchesTheTile() {
        for (surface in listOf(Color.Black, Color.Gray, Color.White)) {
            val badge = tileIconColours(surface, surface)
            assertNotEquals(surface, badge.ink)
            assertNotEquals(surface, badge.circle)
            assertTrue(contrastRatio(badge.ink, badge.circle) >= 3f)
        }
    }
}
