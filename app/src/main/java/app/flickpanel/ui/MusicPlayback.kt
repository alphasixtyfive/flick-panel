package app.flickpanel.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.MEDIA_VOLUME_SET
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.canBrowseMusic
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.text

internal data class MusicPlayback(
    val speaker: String, val entity: Entity?, val available: Boolean, val pending: Boolean,
) {
    val current = entity?.takeIf { available && (it.playingMedia || it.state == "paused") }
    val features = entity?.mediaFeatures ?: 0L
    val ready = available && !pending
    val action = if (entity?.playingMedia == true) MediaTransport.PAUSE else MediaTransport.PLAY
    val canPlayPause = features.hasMediaFeature(action.bit)
    val canBrowse = features.canBrowseMusic()
    val title = current?.attributes?.text("media_title") ?: if (available) "Nothing playing" else "Player unavailable"
    val artist = current?.attributes?.let { it.text("media_artist") ?: it.text("media_album_name") }
    val volume = entity?.volumePercent?.takeIf { features.hasMediaFeature(MEDIA_VOLUME_SET) }
}

@Composable
internal fun PlayPauseButton(
    playback: MusicPlayback, transport: (MediaTransport) -> Unit, modifier: Modifier,
    prominent: Boolean = false, enabled: Boolean = true,
) {
    if (!playback.canPlayPause) return
    val pausing = playback.action == MediaTransport.PAUSE
    val press = { transport(playback.action) }
    val glyph: @Composable () -> Unit = {
        Icon(if (pausing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (pausing) "Pause" else "Play", Modifier.size(if (prominent) 36.dp else 30.dp))
    }
    if (prominent) FilledIconButton(press, modifier, enabled = enabled && playback.ready, content = glyph)
    else IconButton(press, modifier, enabled = enabled && playback.ready, content = glyph)
}
