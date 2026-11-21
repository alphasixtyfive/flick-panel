package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.adjacentTemperature
import app.flickpanel.ha.targetTemperature

@Composable
internal fun ClimateTile(
    label: String, entity: Entity?, live: Entity?, pending: String?, enabled: Boolean,
    modifier: Modifier, step: (Boolean) -> Unit,
) {
    val current = entity?.attributes?.optDouble("current_temperature")?.takeIf(Double::isFinite)
    val setpoint = pending ?: entity?.targetTemperature?.let { "${formatNumber(it, 1)}°" }
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TileTitle(label, Modifier.weight(1f))
                if (current != null) TileStatus("${formatNumber(current, 1)}°", maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (setpoint != null) {
                        TileStatus("Set to", maxLines = 1)
                        TileValue(setpoint)
                    } else {
                        TileStatus("No setpoint")
                    }
                }
                FilledTonalIconButton(onClick = { step(false) }, modifier = Modifier.size(48.dp),
                    enabled = enabled && live?.adjacentTemperature(increase = false) != null) {
                    Icon(Icons.Rounded.Remove, "Lower set temperature")
                }
                FilledTonalIconButton(onClick = { step(true) }, modifier = Modifier.size(48.dp),
                    enabled = enabled && live?.adjacentTemperature(increase = true) != null) {
                    Icon(Icons.Rounded.Add, "Raise set temperature")
                }
            }
        }
    }
}
