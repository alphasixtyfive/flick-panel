package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.MEDIA_SEEK
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.playbackProgress
import app.flickpanel.ha.text

@Composable
internal fun NowPlaying(
    playback: MusicPlayback,
    artwork: ImageBitmap?,
    browse: (() -> Unit)?,
    close: () -> Unit,
    transport: (MediaTransport) -> Unit,
    seek: (Double) -> Unit,
    volume: SpeakerVolume?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val base = colors.surfaceContainer
    val tint = remember(artwork, base) {
        val bitmap = artwork?.asAndroidBitmap()
        val accent = bitmap?.takeIf { it.config != Bitmap.Config.HARDWARE && !it.isRecycled }
            ?.let { Color(it.getPixel(it.width / 3, it.height / 3)) }
        if (accent == null) base else lerp(base, accent, .24f)
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, .88f to colors.surface))
            .safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val artworkHeight = (maxHeight * .51f).coerceIn(146.dp, 380.dp)
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(playback.speaker, Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = close, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, "Close music")
                    }
                }
                val current = playback.current
                MusicArtwork(artwork, Modifier.fillMaxWidth().height(artworkHeight), fallbackSize = 52.dp,
                    shape = MaterialTheme.shapes.large)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        playback.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (current == null) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    playback.artist?.let { artist ->
                        Text(artist, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    current?.playbackProgress()?.let { timeline ->
                        val track = current.attributes.text("media_content_id") ?: playback.title
                        PlaybackTimeline(timeline, track, playback.features.hasMediaFeature(MEDIA_SEEK), playback.ready, seek)
                    }
                }
                PlayerActionRow(playback, browse, transport, volume)
            }
        }
    }
}

@Composable
private fun PlayerActionRow(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit, volume: SpeakerVolume?,
) {
    val libraryAction: @Composable () -> Unit = {
        if (browse != null) {
            IconButton(onClick = browse, enabled = playback.available, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.LibraryMusic, "Browse music")
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val browseBesidePlayback = maxWidth >= 280.dp
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (browseBesidePlayback) {
                    Box(Modifier.align(Alignment.CenterStart)) { libraryAction() }
                }
                TransportButtons(playback, transport)
            }
            if (volume != null || !browseBesidePlayback && browse != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!browseBesidePlayback) libraryAction()
                    if (volume != null) PlayerVolumeControl(volume)
                }
            }
        }
    }
}

@Composable
private fun TransportButtons(playback: MusicPlayback, transport: (MediaTransport) -> Unit) {
    val canPrevious = playback.features.hasMediaFeature(MediaTransport.PREVIOUS.bit)
    val canNext = playback.features.hasMediaFeature(MediaTransport.NEXT.bit)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canPrevious) {
            IconButton(onClick = { transport(MediaTransport.PREVIOUS) }, enabled = playback.ready, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous track", Modifier.size(28.dp))
            }
        } else if (playback.canPlayPause && canNext) {
            Spacer(Modifier.size(48.dp))
        }
        if (playback.canPlayPause) {
            PlayPauseButton(playback, transport, Modifier.size(64.dp), prominent = true)
        }
        if (canNext) {
            IconButton(onClick = { transport(MediaTransport.NEXT) }, enabled = playback.ready, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next track", Modifier.size(28.dp))
            }
        } else if (playback.canPlayPause && canPrevious) {
            Spacer(Modifier.size(48.dp))
        }
    }
}
