package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier = modifier,
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.Star, null, Modifier.size(22.dp), tint = colors.primary)
                TileTitle(label, Modifier.weight(1f))
                if (location != null) Icon(Icons.Rounded.ArrowOutward, "Open map", Modifier.size(19.dp), tint = colors.onSurfaceVariant)
            }
            val status = person?.personStatus()
            if (status != null) TileValue(status, maxLines = 2)
            else TileStatus("Unavailable")
        }
    }
}
