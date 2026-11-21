package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link
import app.flickpanel.ha.MEDIA_SEEK
import app.flickpanel.ha.MEDIA_VOLUME_SET
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.PlaybackProgress
import app.flickpanel.ha.canBrowseMusic
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.playbackProgress
import app.flickpanel.ha.text
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun MusicPlayerScreen(id: String, state: PanelUi, model: PanelViewModel) {
    val entity = state.entities[id]
    val label = state.config.favourites.firstOrNull { it.entity == id }?.let(state::label) ?: "Music"
    val active = state.link == Link.ONLINE && entity?.available == true
    val canBrowse = entity?.mediaFeatures?.canBrowseMusic() == true
    var browsing by rememberSaveable(id) { mutableStateOf(false) }
    if (browsing && canBrowse) {
        MusicBrowser(id, label, model, close = { browsing = false })
        return
    }
    val art = rememberPanelImage(entity?.attributes?.text("entity_picture")
        ?.takeIf { active && (entity.playingMedia || entity.state == "paused") },
        model::cachedArtwork, model::loadArtwork)
    BackHandler(onBack = model::closeMusic)
    NowPlaying(
        label, entity, art, active, state.pending[id], close = model::closeMusic,
        browse = if (canBrowse) ({ browsing = true }) else null,
        transport = { model.mediaTransport(id, it) }, seek = { model.seekMusic(id, it) },
        volume = { level -> entity?.let { model.setVolume(it, level) } },
    )
}

@Composable
internal fun NowPlaying(
    label: String, entity: Entity?, artwork: ImageBitmap?, available: Boolean, pending: String?, close: () -> Unit,
    browse: (() -> Unit)?, transport: (MediaTransport) -> Unit, seek: (Double) -> Unit, volume: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val level = entity?.volumePercent?.takeIf { entity.mediaFeatures.hasMediaFeature(MEDIA_VOLUME_SET) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding(), contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= 680.dp
        val inset = if (wide) 28.dp else 20.dp
        val verticalPadding = 16.dp
        val headerGap = 24.dp
        val headerButtonSize = 48.dp
        val availableHeight = maxHeight
        Column(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = inset, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(headerGap),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (browse != null) {
                    FilledTonalButton(onClick = browse, enabled = available, modifier = Modifier.heightIn(min = headerButtonSize)) {
                        if (wide) Icon(Icons.Rounded.LibraryMusic, null, Modifier.padding(end = 8.dp))
                        Text("Browse")
                    }
                }
                if (level != null) {
                    PlayerVolumeButton(entity.id, level, available, pending == null, volume)
                }
                FilledTonalIconButton(onClick = close, modifier = Modifier.size(headerButtonSize)) {
                    Icon(Icons.Rounded.Close, "Close music")
                }
            }
            if (wide) {
                val coverWidth = (availableHeight * .65f).coerceIn(112.dp, 340.dp)
                val bodyHeight = (availableHeight - verticalPadding * 2 - headerGap - headerButtonSize).coerceAtLeast(0.dp)
                Row(Modifier.fillMaxWidth().heightIn(min = bodyHeight), horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    MusicArtwork(artwork, Modifier.widthIn(max = coverWidth).weight(1f, fill = false).aspectRatio(1f), 76.dp)
                    PlaybackControls(entity, available, pending, transport, seek, Modifier.weight(1.2f))
                }
            } else {
                val coverWidth = (availableHeight * .38f).coerceIn(112.dp, 256.dp)
                MusicArtwork(artwork, Modifier.align(Alignment.CenterHorizontally).widthIn(max = coverWidth)
                    .fillMaxWidth().aspectRatio(1f), 65.dp)
                PlaybackControls(entity, available, pending, transport, seek, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun MusicArtwork(artwork: ImageBitmap?, modifier: Modifier, fallbackSize: Dp = 28.dp) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(MaterialTheme.shapes.large).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (artwork == null) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(fallbackSize), tint = colors.primary)
        } else {
            Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun PlaybackControls(
    entity: Entity?, available: Boolean, pending: String?,
    transport: (MediaTransport) -> Unit, seek: (Double) -> Unit, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val current = entity?.takeIf { available && (it.playingMedia || it.state == "paused") }
    val attrs = current?.attributes
    val features = entity?.mediaFeatures ?: 0L
    val playing = entity?.playingMedia == true
    val ready = available && pending == null
    val action = if (playing) MediaTransport.PAUSE else MediaTransport.PLAY
    val title = attrs?.text("media_title") ?: if (available) "Nothing playing" else "Player unavailable"
    val artist = attrs?.text("media_artist") ?: attrs?.text("media_album_name")
    val timeline = current?.playbackProgress()
    val skipColors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (artist != null) Text(artist, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (timeline != null) {
            PlaybackTimeline(timeline, attrs?.text("media_content_id") ?: title, features.hasMediaFeature(MEDIA_SEEK), ready, seek)
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (features.hasMediaFeature(MediaTransport.PREVIOUS.bit)) {
                IconButton(onClick = { transport(MediaTransport.PREVIOUS) }, enabled = ready,
                    modifier = Modifier.size(48.dp), colors = skipColors) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous track", Modifier.size(30.dp))
                }
            }
            if (features.hasMediaFeature(action.bit)) {
                FilledIconButton(onClick = { transport(action) }, enabled = ready, modifier = Modifier.size(76.dp, 60.dp)) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", Modifier.size(34.dp))
                }
            }
            if (features.hasMediaFeature(MediaTransport.NEXT.bit)) {
                IconButton(onClick = { transport(MediaTransport.NEXT) }, enabled = ready,
                    modifier = Modifier.size(48.dp), colors = skipColors) {
                    Icon(Icons.Rounded.SkipNext, "Next track", Modifier.size(30.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackTimeline(
    timeline: PlaybackProgress, track: String, canSeek: Boolean, enabled: Boolean, seek: (Double) -> Unit,
) {
    val advancing = timeline.advancingSince
    var now by remember(advancing) { mutableStateOf(Instant.now()) }
    LaunchedEffect(advancing) {
        if (advancing == null) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            now = Instant.now()
        }
    }
    var drag by remember(track) { mutableStateOf<Float?>(null) }
    val position = drag?.let { it * timeline.duration } ?: timeline.positionAt(now)
    val progress = (position / timeline.duration).toFloat()
    val colors = MaterialTheme.colorScheme
    Column {
        if (canSeek) {
            val interaction = remember { MutableInteractionSource() }
            Slider(
                value = progress,
                onValueChange = { drag = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    contentDescription = "Playback position"
                },
                enabled = enabled,
                interactionSource = interaction,
                onValueChangeFinished = {
                    val dragged = drag
                    drag = null
                    dragged?.let { seek(it * timeline.duration) }
                },
                thumb = { SliderDefaults.Thumb(interaction, enabled = enabled, thumbSize = DpSize(12.dp, 12.dp)) },
                track = { PlaybackTrack(it.value, enabled) },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(48.dp).semantics {
                    contentDescription = "Playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
                contentAlignment = Alignment.Center,
            ) { PlaybackTrack(progress, enabled = true) }
        }
        val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "'tnum'")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackTime(position), color = colors.onSurfaceVariant, style = style)
            Text(playbackTime(timeline.duration), color = colors.onSurfaceVariant, style = style)
        }
    }
}

private fun playbackTime(seconds: Double): String {
    val total = seconds.toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
private fun PlaybackTrack(progress: Float, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.outlineVariant)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
            .background(if (enabled) colors.primary else colors.onSurfaceVariant))
    }
}
