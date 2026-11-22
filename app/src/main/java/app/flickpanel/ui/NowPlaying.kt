package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.MEDIA_SEEK
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.playbackProgress
import app.flickpanel.ha.text

private val playerWidth = 540.dp

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
    val idle = playback.current == null
    val visibleVolume = volume.takeIf { playback.available && !playback.off }
    val base = colors.surfaceContainer
    val tint = remember(artwork, base) {
        val bitmap = artwork?.asAndroidBitmap()
        val accent = bitmap?.takeIf { it.config != Bitmap.Config.HARDWARE && !it.isRecycled }
            ?.let { Color(it.getPixel(it.width / 3, it.height / 3)) }
        if (accent == null) base else lerp(base, accent, .24f)
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, .88f to colors.surface))
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shortLandscape = maxHeight < 444.dp && maxWidth >= 556.dp && maxWidth > maxHeight
        val artworkHeight = (maxHeight * if (volume == null) .51f else .46f).coerceIn(146.dp, 380.dp)
        val artworkSize = (maxHeight * .56f).coerceIn(128.dp, 212.dp)
        val width = (if (shortLandscape) 720.dp else playerWidth) + 36.dp
        Column(Modifier.widthIn(max = width).fillMaxWidth().fillMaxHeight()) {
            ViewerTopAppBar(
                playback.speaker, "Close music", close,
                windowInsets = WindowInsets(0, 0, 0, 0), containerColor = Color.Transparent,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                if (shortLandscape) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MusicArtwork(artwork, Modifier.size(artworkSize), fallbackSize = 48.dp,
                            shape = MaterialTheme.shapes.large)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (idle) {
                                IdlePlayer(playback, browse, transport, showIcon = false)
                            } else {
                                TrackControls(playback, browse, transport, seek)
                            }
                            if (visibleVolume != null) VolumeRow(visibleVolume, Modifier.padding(top = 8.dp))
                        }
                    }
                } else {
                    if (idle) {
                        Surface(
                            Modifier.fillMaxWidth().heightIn(min = artworkHeight),
                            shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHighest,
                        ) {
                            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                IdlePlayer(playback, browse, transport)
                            }
                        }
                    } else {
                        MusicArtwork(artwork, Modifier.fillMaxWidth().height(artworkHeight), fallbackSize = 52.dp,
                            shape = MaterialTheme.shapes.large)
                        TrackControls(playback, browse, transport, seek)
                    }
                    if (visibleVolume != null) {
                        VolumeRow(visibleVolume, Modifier.padding(top = if (idle) 16.dp else 40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdlePlayer(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit,
    showIcon: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = if (showIcon) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showIcon) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = colors.onSurfaceVariant)
        }
        Text(
            playback.title, style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface, textAlign = if (showIcon) TextAlign.Center else TextAlign.Start,
        )
        if (playback.available && !playback.off && (browse != null || playback.canPlayPause)) {
            val label = if (browse != null) "Browse music" else "Play"
            val icon = if (browse != null) Icons.Rounded.LibraryMusic else Icons.Rounded.PlayArrow
            Button(
                onClick = browse ?: { transport(MediaTransport.PLAY) },
                enabled = playback.ready, modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun VolumeRow(volume: SpeakerVolume, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PlayerVolumeControl(volume, Modifier.widthIn(max = 300.dp).fillMaxWidth())
    }
}

@Composable
private fun TrackControls(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit,
    seek: (Double) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val current = playback.current ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            playback.status?.let { PlaybackIndicator(it) }
            playback.trackTitle?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            playback.artist?.let { artist ->
                Text(artist, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            current.playbackProgress()?.let { timeline ->
                val track = current.attributes.text("media_content_id") ?: playback.title
                PlaybackTimeline(timeline, track, playback.features.hasMediaFeature(MEDIA_SEEK), playback.ready, seek)
            }
        }
        PlayerActionRow(playback, browse, transport)
    }
}

@Composable
private fun PlayerActionRow(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit,
) {
    val libraryAction: @Composable () -> Unit = {
        if (browse != null) {
            IconButton(onClick = browse, enabled = playback.available, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.LibraryMusic, "Browse music",
                    tint = if (playback.available) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val browseBesidePlayback = maxWidth >= 280.dp
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (browseBesidePlayback) {
                    Box(Modifier.align(Alignment.CenterStart)) { libraryAction() }
                }
                TransportButtons(playback, transport)
            }
            if (!browseBesidePlayback) libraryAction()
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
