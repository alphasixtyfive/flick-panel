package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.CoverAction
import app.flickpanel.ha.Entity
import app.flickpanel.ha.canMoveCover
import app.flickpanel.ha.coverPosition
import app.flickpanel.ha.gateToggleAction
import app.flickpanel.ha.supportsCoverAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPolicyTest {
    @Test fun gateTileTogglesTheCurrentOrPartlyOpenPosition() {
        assertEquals(CoverAction.OPEN, cover("closed").gateToggleAction())
        assertEquals(CoverAction.CLOSE, cover("open").gateToggleAction())
        assertEquals(CoverAction.CLOSE, cover("open", position = 42).gateToggleAction())
        assertNull(cover("open", features = 1).gateToggleAction())
        assertNull(cover("closed").gateToggleAction(CoverAction.OPEN.progress))
        for (state in listOf("opening", "closing", "unknown", "unavailable")) {
            assertNull(cover(state).gateToggleAction())
        }
    }

    @Test fun onlyHomeAssistantAdvertisedActionsBecomeControls() {
        val gate = cover("closed", features = 3)
        assertTrue(gate.gateWithOpenControl)
        assertEquals(listOf(TileKind.COVER, TileKind.VALUE), gate.kinds())
        assertTrue(gate.supportsCoverAction(CoverAction.OPEN))
        assertTrue(gate.supportsCoverAction(CoverAction.CLOSE))
        assertFalse(gate.supportsCoverAction(CoverAction.STOP))
        assertEquals(listOf(TileKind.VALUE), cover("open", features = 32).kinds())
        assertFalse(gate.copy(id = "switch.other").supportsCoverAction(CoverAction.OPEN))
    }

    @Test fun gateSupportsOnlyDirectionsThatChangeItsPosition() {
        val closed = cover("closed")
        assertTrue(closed.canMoveCover(CoverAction.OPEN))
        assertFalse(closed.canMoveCover(CoverAction.CLOSE))
        val partial = cover("open", position = 42)
        assertTrue(partial.canMoveCover(CoverAction.CLOSE))
        assertTrue(partial.canMoveCover(CoverAction.OPEN))
        assertFalse(cover("open", position = 100).canMoveCover(CoverAction.OPEN))
        assertFalse(cover("open").canMoveCover(CoverAction.OPEN))
        assertTrue(cover("open").canMoveCover(CoverAction.CLOSE))
    }

    @Test fun stopIsAvailableDuringMotionAndWhileAMoveIsPending() {
        val moving = cover("opening")
        assertTrue(moving.canMoveCover(CoverAction.STOP))
        assertFalse(moving.canMoveCover(CoverAction.CLOSE))
        assertFalse(moving.canMoveCover(CoverAction.OPEN))
        assertFalse(moving.canMoveCover(CoverAction.STOP, CoverAction.STOP.progress))
        assertTrue(cover("closed").canMoveCover(CoverAction.STOP, CoverAction.OPEN.progress))
        assertFalse(cover("closed").canMoveCover(CoverAction.STOP))
        assertFalse(cover("open").canMoveCover(CoverAction.CLOSE, CoverAction.OPEN.progress))
        for (state in listOf("unavailable", "unknown", "", "malformed")) {
            assertFalse(CoverAction.entries.any { cover(state).canMoveCover(it, CoverAction.OPEN.progress) })
        }
    }

    @Test fun invalidPositionsAreIgnored() {
        assertEquals(0, cover("closed", position = 0).coverPosition())
        assertNull(cover("unknown", position = 42).coverPosition())
        for (position in listOf(-1, 101, 2.25, "25")) {
            assertNull(cover("open", position = position).coverPosition())
        }
    }

    @Test fun coverWidthRoundTripsAndOldValueTilesStayReadOnly() {
        val tile = TileSpec("cover.front_gate", TileKind.COVER, wideOnTablet = true)
        assertEquals(listOf(tile), PanelConfigCodec.decode(PanelConfigCodec.encode(PanelConfig(favourites = listOf(tile)))).favourites)
        val legacy = """{"version":1,"tiles":[{"entity":"cover.front_gate","kind":"VALUE"}, {"entity":"switch.other","kind":"COVER"}]}"""
        assertEquals(listOf(tile.copy(kind = TileKind.VALUE, wideOnTablet = false)), PanelConfigCodec.decode(legacy).favourites)
    }

    private fun cover(state: String, features: Long = 11, position: Any? = null) =
        Entity("cover.front_gate", state, JSONObject()
            .put("supported_features", features).put("device_class", "gate").put("current_position", position))
}
