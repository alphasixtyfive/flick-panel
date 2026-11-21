package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.PersonLocation
import app.flickpanel.ha.personStatus

@Composable
internal fun PersonTile(label: String, live: Entity?, online: Boolean, modifier: Modifier, open: () -> Unit) {
    val person = live.takeIf { online }
    val location = person?.let { PersonLocation.from(it) }
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = open,
        enabled = location != null,
        modifier = modifier.semantics { if (location != null) onClick(label = "Open map", action = null) },
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewTileTitle(label, Icons.Rounded.Star, canOpen = location != null)
            val status = person?.personStatus()
            if (status != null) TileValue(status, maxLines = 2)
            else TileStatus("Unavailable")
        }
    }
}
