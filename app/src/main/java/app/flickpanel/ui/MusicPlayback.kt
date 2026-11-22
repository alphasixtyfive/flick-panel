package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.MEDIA_VOLUME_MUTE
import app.flickpanel.ha.MEDIA_VOLUME_SET
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.canBrowseMusic
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.text

internal enum class PlaybackStatus(val label: String) {
    PLAYING("Playing"), BUFFERING("Buffering"), PAUSED("Paused"),
}

internal data class MusicPlayback(
    val speaker: String, val entity: Entity?, val available: Boolean, val pending: Boolean = false,
) {
    val status = when (entity?.state?.takeIf { available }) {
        "playing" -> PlaybackStatus.PLAYING
        "buffering" -> PlaybackStatus.BUFFERING
        "paused" -> PlaybackStatus.PAUSED
        else -> null
    }
    val current = entity?.takeIf { status != null }
    val off get() = available && entity?.state == "off"
    val trackTitle get() = current?.attributes?.text("media_title")
    val artworkUrl get() = current?.attributes?.text("entity_picture")
    val title get() = trackTitle ?: when {
        status != null -> speaker
        off -> "Speaker off"
        available -> "Nothing playing"
        else -> "Player unavailable"
    }
    val artist get() = current?.attributes?.let { it.text("media_artist") ?: it.text("media_album_name") }
    val features get() = entity?.mediaFeatures ?: 0L
    val ready get() = available && !pending
    val action get() = if (current?.playingMedia == true) MediaTransport.PAUSE else MediaTransport.PLAY
    val canPlayPause get() = features.hasMediaFeature(action.bit)
    val canBrowse get() = features.canBrowseMusic()
    val volume get() = entity?.volumePercent?.takeIf { features.hasMediaFeature(MEDIA_VOLUME_SET) }
    val muted get() = entity?.volumeMuted?.takeIf { features.hasMediaFeature(MEDIA_VOLUME_MUTE) }
}

@Composable
internal fun PlaybackIndicator(status: PlaybackStatus, label: String = status.label) {
    val colors = MaterialTheme.colorScheme
    val tint = if (status == PlaybackStatus.PAUSED) colors.onSurfaceVariant else colors.primary
    val icon = when (status) {
        PlaybackStatus.PLAYING -> Icons.Rounded.Equalizer
        PlaybackStatus.BUFFERING -> Icons.Rounded.HourglassTop
        PlaybackStatus.PAUSED -> Icons.Rounded.Pause
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
