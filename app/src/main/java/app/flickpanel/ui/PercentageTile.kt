package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link

@Composable
internal fun PercentageTile(
    label: String, entity: Entity?, link: Link, modifier: Modifier, accent: TileAccent?, icon: ImageVector? = null,
) {
    val reading = entity?.percentageReading
    val surface = tileContainerColour(accent)
    Surface(modifier, shape = panelCardShape, color = surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().padding(tileContentPadding), verticalArrangement = Arrangement.SpaceBetween) {
            AccentedTileTitle(label, Icons.Rounded.Percent, accent, customIcon = icon, surface = surface)
            if (reading == null || link == Link.AUTH_REJECTED) {
                TileStatus("Unavailable")
            } else {
                val value = remember(reading) { "${formatNumber(reading.toDouble(), 1)}%" }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TileValue(value)
                    LinearProgressIndicator(progress = { reading / 100f }, modifier = Modifier.fillMaxWidth(),
                        color = tileAccentColour(accent, surface))
                    if (link == Link.OFFLINE) TileStatus("Last reported")
                }
            }
        }
    }
}
