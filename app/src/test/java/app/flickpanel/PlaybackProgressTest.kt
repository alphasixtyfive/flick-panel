package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.playbackProgress
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressTest {
    private val updated = Instant.parse("2026-11-21T12:00:00Z")
    private val attributes = JSONObject().put("media_duration", 120)
        .put("media_position", 40).put("media_position_updated_at", updated.toString())
    private val playing = Entity("media_player.kitchen", "playing", attributes)

    @Test fun playingPositionAdvancesAndStaysWithinTheTrack() {
        val progress = checkNotNull(playing.playbackProgress())
        assertEquals(40.0, progress.positionAt(updated.minusSeconds(8)), 0.01)
        assertEquals(53.0, progress.positionAt(updated.plusSeconds(13)), 0.01)
        assertEquals(120.0, progress.positionAt(updated.plusSeconds(100)), 0.01)
    }

    @Test fun pausedPositionStaysStillAndMissingDurationOmitsTimeline() {
        val paused = checkNotNull(playing.copy(state = "paused").playbackProgress())
        assertEquals(40.0, paused.positionAt(updated.plusSeconds(30)), 0.01)
        assertNull(playing.copy(attributes = JSONObject().put("media_position", 40)).playbackProgress())
    }
}
