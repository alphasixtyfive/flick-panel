package app.flickpanel.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity

@Composable
internal fun MusicTile(
    label: String, entity: Entity?, available: Boolean, model: PanelViewModel, modifier: Modifier, accent: TileAccent?,
    compact: Boolean, icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tileSurface = tileContainerColour(accent)
    val playback = MusicPlayback(label, entity, available)
    val cover = rememberPanelImage(playback.artworkUrl, model::cachedArtwork, model::loadArtwork)
    val status = playback.status
    Surface(
        onClick = { entity?.let { model.openMusic(it.id) } }, enabled = available,
        modifier = modifier.semantics { if (available) onClick(label = "Open music player", action = null) },
        shape = panelCardShape, color = tileSurface, contentColor = colors.onSurface,
    ) {
        ControlTileContent(
            label, compact, titleLines = 1,
            headingDetail = { status?.let { PlaybackIndicator(it) } },
            badge = {
                if (cover != null) MusicArtwork(cover, Modifier.size(tileIconSize))
                else TileIconBadge(icon ?: Icons.Rounded.MusicNote, tileSurface, tileAccentColour(accent, tileSurface))
            },
        ) {
            val detailColor = if (status == null) colors.onSurfaceVariant else colors.onSurface
            val track = playback.trackTitle
            val message = when {
                status == null -> if (available) "Nothing playing" else "Unavailable"
                compact && track != null -> "${status.label} · $track"
                compact -> status.label
                else -> track
            }
            if (message != null) {
                if (compact) CompactControlStatus(message, color = detailColor)
                else TileStatus(message, maxLines = 1, color = detailColor)
            }
        }
    }
}
