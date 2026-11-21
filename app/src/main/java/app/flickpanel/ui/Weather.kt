package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.weather

@Composable
internal fun WeatherTile(label: String, entity: Entity?, modifier: Modifier) {
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TileTitle(label)
            if (entity?.weather() == null) TileStatus("Unavailable")
            else WeatherSummary(entity, compact = false)
        }
    }
}

@Composable
internal fun WeatherSummary(entity: Entity?, compact: Boolean, prominent: Boolean = false) {
    val reading = entity?.weather() ?: return
    val icon = when (entity.state) {
        "sunny" -> Icons.Rounded.WbSunny
        "clear-night" -> Icons.Rounded.NightsStay
        "partlycloudy" -> Icons.Rounded.WbCloudy
        "lightning", "lightning-rainy" -> Icons.Rounded.Thunderstorm
        "rainy", "pouring" -> Icons.Rounded.Umbrella
        "windy", "windy-variant" -> Icons.Rounded.Air
        else -> Icons.Rounded.Cloud
    }
    val type = MaterialTheme.typography
    val iconSize = when {
        prominent -> 38.dp
        compact -> 23.dp
        else -> 28.dp
    }
    val gap = if (prominent) 12.dp else 8.dp
    val temperatureStyle = if (prominent) type.displaySmall else type.titleLarge
    val showCondition = !compact && !prominent
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, if (showCondition) null else reading.condition, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize))
        Column {
            reading.temperature?.let {
                val unit = reading.unit.ifEmpty { "°" }
                Text(formatNumber(it, 0) + unit, style = temperatureStyle, maxLines = 1)
            }
            if (showCondition) {
                Text(reading.condition, style = type.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
