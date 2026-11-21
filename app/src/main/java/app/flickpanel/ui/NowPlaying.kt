package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    volume: (Int) -> Unit,
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
    val level = playback.volume
    var volumeOpen by remember(playback.entity?.id, playback.available) { mutableStateOf(false) }
    val showVolume = volumeOpen && level != null
    BackHandler(enabled = showVolume) { volumeOpen = false }
    val volumeButton: @Composable () -> Unit = {
        if (level != null) PlayerVolumeButton(level, showVolume, playback.available) { volumeOpen = !volumeOpen }
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
                    Text(playback.speaker, Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = close, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, "Close music")
                    }
                }
                val current = playback.current
                if (current == null) {
                    IdlePlayer(playback, browse, transport, volumeButton)
                    if (volumeOpen && level != null) PlayerVolumeControl(level, playback.ready, volume)
                } else {
                    FeaturedTrack(playback, artwork, artworkHeight)
                    if (volumeOpen && level != null) {
                        PlayerVolumeControl(level, playback.ready, volume)
                    } else {
                        current.playbackProgress()?.let { timeline ->
                            val track = current.attributes.text("media_content_id") ?: playback.title
                            PlaybackTimeline(timeline, track, playback.features.hasMediaFeature(MEDIA_SEEK), playback.ready, seek)
                        }
                    }
                    PlayerActionRow(playback, browse, transport, volumeButton)
                }
            }
        }
    }
}

@Composable
private fun FeaturedTrack(playback: MusicPlayback, artwork: ImageBitmap?, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.large)) {
        MusicArtwork(artwork, Modifier.matchParentSize(), fallbackSize = 64.dp, shape = MaterialTheme.shapes.large)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .43f to Color(0x24090d11), 1f to Color(0xf4090d11),
        )))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(playback.title, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            playback.artist?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xffe6ebf1),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun IdlePlayer(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit, volumeButton: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MusicArtwork(null, Modifier.size(76.dp), fallbackSize = 38.dp)
        Text(playback.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (browse != null) {
                FilledTonalButton(onClick = browse, enabled = playback.available, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Browse music")
                }
            }
            if (playback.canPlayPause) {
                IconButton(onClick = { transport(playback.action) }, enabled = playback.ready, modifier = Modifier.size(48.dp)) {
                    PlayPauseIcon(playback.action, 28.dp)
                }
            }
            volumeButton()
        }
    }
}

@Composable
private fun PlayerActionRow(
    playback: MusicPlayback, browse: (() -> Unit)?, transport: (MediaTransport) -> Unit, volumeButton: @Composable () -> Unit,
) {
    val libraryAction: @Composable () -> Unit = {
        if (browse != null) {
            IconButton(onClick = browse, enabled = playback.available, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.LibraryMusic, "Browse music")
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TransportButtons(playback, transport)
                if (browse != null || playback.volume != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        libraryAction()
                        volumeButton()
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { libraryAction() }
                TransportButtons(playback, transport)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { volumeButton() }
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
            FilledIconButton(onClick = { transport(playback.action) }, enabled = playback.ready, modifier = Modifier.size(64.dp)) {
                PlayPauseIcon(playback.action, 36.dp)
            }
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

@Composable
internal fun MusicPlaybackBar(
    playback: MusicPlayback, artwork: ImageBitmap?, showPlayer: () -> Unit,
    transport: (MediaTransport) -> Unit, volume: (Int) -> Unit, starting: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val level = playback.volume
    var volumeOpen by remember(playback.entity?.id, playback.available) { mutableStateOf(false) }
    BackHandler(enabled = volumeOpen && level != null) { volumeOpen = false }
    Column(Modifier.fillMaxWidth().clip(panelCardShape).background(colors.surfaceContainerHigh)) {
        if (volumeOpen && level != null) {
            PlayerVolumeControl(level, playback.ready && !starting, volume,
                Modifier.align(Alignment.End).widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(enabled = !starting, role = Role.Button,
                onClickLabel = "Open Now Playing", onClick = showPlayer)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MusicArtwork(artwork, Modifier.size(44.dp), fallbackSize = 23.dp)
                Column {
                    Text(playback.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playback.speaker, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (playback.canPlayPause) {
                IconButton(onClick = { transport(playback.action) }, enabled = playback.ready && !starting, modifier = Modifier.size(48.dp)) {
                    PlayPauseIcon(playback.action, 30.dp)
                }
            }
            if (level != null) {
                PlayerVolumeButton(level, volumeOpen, playback.available && !starting) { volumeOpen = !volumeOpen }
            }
        }
    }
}

@Composable
private fun PlayPauseIcon(action: MediaTransport, size: Dp) {
    val pausing = action == MediaTransport.PAUSE
    Icon(if (pausing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        if (pausing) "Pause" else "Play", Modifier.size(size))
}

@Composable
internal fun MusicArtwork(
    artwork: ImageBitmap?, modifier: Modifier, fallbackSize: Dp = 28.dp, shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (artwork == null) Icon(Icons.Rounded.MusicNote, null, Modifier.size(fallbackSize), tint = colors.primary)
        else Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}
