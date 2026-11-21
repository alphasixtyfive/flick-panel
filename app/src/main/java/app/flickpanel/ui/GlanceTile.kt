package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GlanceReading
import app.flickpanel.ha.glanceReading

@Composable
internal fun GlanceTile(label: String, entity: Entity?, available: Boolean, modifier: Modifier) {
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
    Surface(modifier.widthIn(min = 98.dp, max = 164.dp).heightIn(min = 70.dp)
        .clearAndSetSemantics { contentDescription = "$label, ${value ?: "Unavailable"}" },
        shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(25.dp), tint = if (value == null) colors.onSurfaceVariant else colors.primary)
            TileValue(value ?: "—")
        }
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
