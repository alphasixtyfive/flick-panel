package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity

@Composable
internal fun ClimateTile(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier,
    entity: Entity?, enabled: Boolean, compact: Boolean,
) {
    val info = entity ?: state.entity(tile.entity)
    val current = info?.attributes?.optDouble("current_temperature")?.takeIf(Double::isFinite)
    val temperature = current?.let { formatNumber(it, 1) + "°" } ?: "—"
    val label = state.label(tile)
    val pending = state.pending[tile.entity]
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        if (compact) {
            Column(Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(temperature, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                TemperatureSetpoint(info, entity, pending, enabled, model, compact = true)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(19.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(temperature, style = MaterialTheme.typography.headlineMedium)
                }
                TemperatureSetpoint(info, entity, pending, enabled, model, compact = false)
            }
        }
    }
}

@Composable
private fun TemperatureSetpoint(
    info: Entity?,
    live: Entity?,
    pending: String?,
    enabled: Boolean,
    model: PanelViewModel,
    compact: Boolean,
) {
    val attrs = info?.attributes
    val target = attrs?.optDouble("temperature")?.takeIf(Double::isFinite)
    val step = attrs?.optDouble("target_temp_step", .5)?.takeIf { it > 0 } ?: .5
    val adjustable = enabled && live != null && target != null
    Row(
        if (compact) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(if (compact) Modifier.weight(1f) else Modifier.padding(end = 10.dp)) {
            Text("Set to", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(pending ?: target?.let { formatNumber(it, 1) + "°" } ?: "—",
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target - step) },
            enabled = adjustable, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove, "Lower target") }
        FilledTonalIconButton(onClick = { if (live != null && target != null) model.setTemperature(live, target + step) },
            enabled = adjustable, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Raise target") }
    }
}
