package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.MediaTransport

@Composable
internal fun MusicPlaybackBar(
    playback: MusicPlayback, artwork: ImageBitmap?, showPlayer: () -> Unit,
    transport: (MediaTransport) -> Unit, volume: SpeakerVolume?, starting: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().clip(panelCardShape).background(colors.surfaceContainerHigh)) {
        val inlineVolume = maxWidth >= 400.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(enabled = !starting, role = Role.Button,
                    onClickLabel = "Open Now Playing", onClick = showPlayer)
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicArtwork(artwork, Modifier.size(44.dp), fallbackSize = 23.dp)
                    Column {
                        Text(playback.title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(playback.speaker, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PlayPauseButton(playback, transport, Modifier.size(48.dp), enabled = !starting)
                if (inlineVolume && volume != null) PlayerVolumeControl(volume, enabled = !starting)
            }
            if (!inlineVolume && volume != null) {
                PlayerVolumeControl(volume, Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp), enabled = !starting)
            }
        }
    }
}
