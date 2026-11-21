package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.PanelTheme
import app.flickpanel.config.SavedRemote
import app.flickpanel.config.restoreSavedPanel
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.parseServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPanelRecoveryTest {
    @Test fun unreadableLayoutKeepsTheExistingSetupAndCredentials() {
        val credentials = HaCredentials(parseServer("https://example.invalid"), "fixture-token")
        val remote = SavedRemote("fixture-panel", "Fixture Panel")
        for (raw in listOf("{", """{"version":2,"tiles":[]}""")) {
            val saved = restoreSavedPanel(raw, configured = true, credentials = credentials, remote = remote)
            assertTrue(saved.configured)
            assertTrue(saved.layoutUnreadable)
            assertSame(credentials, saved.credentials)
            assertSame(remote, saved.remote)
            assertEquals(PanelConfig(), saved.config)
        }
    }

    @Test fun validAndAbsentLayoutsDoNotReportRecovery() {
        val config = PanelConfig(theme = PanelTheme.AMBER, idleSeconds = 0)
        val valid = restoreSavedPanel(PanelConfigCodec.encode(config), configured = true, credentials = null, remote = null)
        val absent = restoreSavedPanel(null, configured = false, credentials = null, remote = null)
        assertEquals(config, valid.config)
        assertFalse(valid.layoutUnreadable)
        assertEquals(PanelConfig(), absent.config)
        assertFalse(absent.layoutUnreadable)
        assertFalse(absent.configured)
    }
}
