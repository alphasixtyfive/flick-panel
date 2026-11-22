package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ui.MusicPlayback
import app.flickpanel.ui.PlaybackStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPlaybackTest {
    private val id = "media_player.kitchen"

    @Test fun playingAndBufferingAreDistinctEvenWithoutTrackMetadata() {
        val playing = MusicPlayback("Kitchen", Entity(id, "playing"), available = true)
        val buffering = playing.copy(entity = Entity(id, "buffering"))

        assertEquals(PlaybackStatus.PLAYING, playing.status)
        assertEquals(PlaybackStatus.BUFFERING, buffering.status)
        assertEquals("Kitchen", playing.title)
        assertEquals(MediaTransport.PAUSE, buffering.action)
    }

    @Test fun pausedKeepsTheTrackAndIdleOrOfflineClearsStaleMetadata() {
        val track = JSONObject().put("media_title", "The track")
        val paused = MusicPlayback("Kitchen", Entity(id, "paused", track), available = true)
        val idle = paused.copy(entity = Entity(id, "idle", track))
        val offline = paused.copy(available = false)

        assertEquals(PlaybackStatus.PAUSED, paused.status)
        assertEquals("The track", paused.trackTitle)
        assertEquals(MediaTransport.PLAY, paused.action)
        assertNull(idle.status)
        assertNull(idle.trackTitle)
        assertNull(offline.status)
        assertNull(offline.trackTitle)
    }
}
