package app.flickpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Entity
import app.flickpanel.ha.MEDIA_VOLUME_SET
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.canBrowseMusic
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.text

@Composable
internal fun MusicTile(label: String, entity: Entity?, pending: String?, ready: Boolean, model: PanelViewModel, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val features = entity?.mediaFeatures ?: 0L
    val state = entity?.state
    val playing = entity?.playingMedia == true
    val hasCurrent = playing || state == "paused"
    val transport = if (playing) MediaTransport.PAUSE else MediaTransport.PLAY
    val canBrowse = features.canBrowseMusic()
    val volume = entity?.volumePercent?.takeIf { features.hasMediaFeature(MEDIA_VOLUME_SET) }
    val preview = remember(entity?.id) { mutableStateOf<Int?>(null) }
    val adjusting = preview.value ?: pending?.removeSuffix("%")?.toIntOrNull()
    val showTransport = features.hasMediaFeature(transport.bit) && (hasCurrent || !canBrowse)
    val cover = rememberPanelImage(entity?.attributes?.text("entity_picture")?.takeIf { ready && hasCurrent },
        model::cachedArtwork, model::loadArtwork)
    val detail = when {
        adjusting != null -> "Volume $adjusting%"
        pending != null -> pending
        !ready || entity?.available != true -> "Unavailable"
        hasCurrent -> listOfNotNull(entity.attributes.text("media_title"), entity.attributes.text("media_artist"))
            .joinToString(" \u00B7 ").takeIf { it.isNotEmpty() }
        canBrowse -> "Browse music"
        else -> null
    }
    val browseAction = if (canBrowse) {
        Modifier.clickable(enabled = ready, onClickLabel = "Browse music", role = Role.Button) {
            entity?.let { model.openMusic(it.id) }
        }
    } else {
        Modifier
    }
    Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
        Column(
            Modifier.fillMaxSize().clip(panelCardShape).then(browseAction)
                .tileSlider(ready && volume != null, adjusting ?: volume ?: 0, preview) { level ->
                    entity?.let { model.setVolume(it, level) }
                }
                .tileFill((adjusting ?: volume ?: 0) / 100f, colors.primary.copy(alpha = if (adjusting == null) .08f else .22f))
                .padding(tileContentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TileTitle(label, Modifier.weight(1f).align(Alignment.Top))
                Box(Modifier.size(tileArtworkSize), contentAlignment = Alignment.Center) {
                    if (cover != null) {
                        Image(cover, null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(tileIconSize), tint = colors.primary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                TileStatus(detail.orEmpty(), Modifier.weight(1f))
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (showTransport) {
                        FilledTonalIconButton(onClick = { entity?.let { model.mediaTransport(it.id, transport) } },
                            modifier = Modifier.fillMaxSize(), enabled = ready) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playing) "Pause" else "Play", Modifier.size(compactTileIconSize))
                        }
                    } else if (canBrowse) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
