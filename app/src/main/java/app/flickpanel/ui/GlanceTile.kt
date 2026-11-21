package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GlanceReading
import app.flickpanel.ha.Link
import app.flickpanel.ha.glanceReading

@Composable
internal fun GlanceTile(label: String, entity: Entity?, available: Boolean, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        GlanceValue(label, entity, available)
    }
}

@Composable
internal fun HomeGlanceReadings(tiles: List<TileSpec>, state: PanelUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
        for (tile in tiles) key(tile.entity) {
            val available = state.link == Link.ONLINE && state.entities[tile.entity]?.available == true
            GlanceValue(state.label(tile), state.entity(tile.entity), available, prominent = true)
        }
    }
}

@Composable
private fun GlanceValue(label: String, entity: Entity?, available: Boolean, prominent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val reading = entity?.glanceReading
    val icon = when (reading) {
        GlanceReading.TEMPERATURE -> Icons.Rounded.Thermostat
        GlanceReading.HUMIDITY -> Icons.Rounded.WaterDrop
        GlanceReading.POWER -> Icons.Rounded.ElectricBolt
        GlanceReading.BATTERY -> Icons.Rounded.BatteryStd
        GlanceReading.CO2 -> Icons.Rounded.Air
        GlanceReading.LIGHT -> Icons.Rounded.WbSunny
        null -> Icons.Rounded.Sensors
    }
    val value = entity?.takeIf { available }?.let { glanceValue(it, reading) }
    val style = if (prominent) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium
    Row(Modifier.heightIn(min = 48.dp)
        .padding(horizontal = if (prominent) 0.dp else 12.dp, vertical = if (prominent) 0.dp else 6.dp)
        .clearAndSetSemantics { contentDescription = "$label, ${value ?: "Unavailable"}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(if (prominent) 38.dp else 23.dp),
            tint = if (value == null) colors.onSurfaceVariant else colors.primary)
        Text(value ?: "—", if (prominent) Modifier.widthIn(max = 162.dp) else Modifier,
            style = style.copy(fontFeatureSettings = "'tnum'"), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun glanceValue(entity: Entity, reading: GlanceReading?): String? {
    val number = entity.state.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
    val unit = entity.unit
    val precision = when {
        reading == GlanceReading.TEMPERATURE -> 1
        reading == GlanceReading.POWER && unit != "W" -> 2
        else -> 0
    }
    val gap = if (unit.isBlank() || unit == "%" || unit.startsWith('°')) "" else " "
    return formatNumber(number, precision) + gap + unit
}
