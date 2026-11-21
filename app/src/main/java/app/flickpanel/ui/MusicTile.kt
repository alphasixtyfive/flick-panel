package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Entity
import app.flickpanel.ha.text

@Composable
internal fun MusicTile(label: String, entity: Entity?, available: Boolean, model: PanelViewModel, modifier: Modifier) {
    val active = entity?.takeIf { available }
    val current = active?.takeIf { it.playingMedia || it.state == "paused" }
    val cover = rememberPanelImage(current?.attributes?.text("entity_picture"), model::cachedArtwork, model::loadArtwork)
    val detail = when {
        active == null -> "Unavailable"
        current != null -> current.attributes.text("media_title") ?: if (current.playingMedia) "Playing" else "Paused"
        else -> "Nothing playing"
    }
    Surface(
        onClick = { active?.let { model.openMusic(it.id) } }, enabled = active != null,
        modifier = modifier.semantics { if (active != null) onClick(label = "Open music player", action = null) },
        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxSize().padding(tileContentPadding), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TileTitle(label, Modifier.weight(1f).align(Alignment.Top))
                MusicArtwork(cover, Modifier.size(tileIconSize))
            }
            TileStatus(detail)
        }
    }
}
