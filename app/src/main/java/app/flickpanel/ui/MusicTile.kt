package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity

@Composable
internal fun MusicTile(
    label: String, entity: Entity?, available: Boolean, model: PanelViewModel, modifier: Modifier, accent: TileAccent?,
    compact: Boolean,
) {
    val playback = MusicPlayback(label, entity, available)
    val cover = rememberPanelImage(playback.artworkUrl, model::cachedArtwork, model::loadArtwork)
    val status = playback.status
    Surface(
        onClick = { entity?.let { model.openMusic(it.id) } }, enabled = available,
        modifier = modifier.semantics { if (available) onClick(label = "Open music player", action = null) },
        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = tileContentPadding, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    AccentedTileTitle(label, Icons.Rounded.MusicNote, accent.takeIf { cover != null }, maxLines = 1)
                    status?.let { PlaybackIndicator(it) }
                }
                MusicArtwork(cover, Modifier.size(tileIconSize),
                    fallbackTint = tileAccentColour(accent, MaterialTheme.colorScheme.surfaceContainerHighest))
            }
            val detail = if (status == null) {
                if (available) "Nothing playing" else "Unavailable"
            } else playback.trackTitle
            detail?.let {
                TileStatus(it, maxLines = 1,
                    color = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
