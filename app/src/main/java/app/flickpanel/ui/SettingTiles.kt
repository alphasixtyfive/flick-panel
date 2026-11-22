package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity

@Composable
internal fun ChoiceTile(
    label: String, entity: Entity?, pending: String?, enabled: Boolean,
    modifier: Modifier, accent: TileAccent?, select: (String) -> Unit,
) {
    val options = remember(entity?.attributes) { entity?.options.orEmpty() }
    var open by remember(entity?.id) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    Surface(onClick = { open = true }, enabled = enabled && options.isNotEmpty(), modifier = modifier,
        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box {
            Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TileTitle(label, Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(compactTileIconSize), tint = tileAccentColour(accent))
                }
                TileValue(pending ?: entity?.takeIf(Entity::available)?.state ?: "Unavailable", maxLines = 2)
            }
            DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
                for (option in options) {
                    DropdownMenuItem(text = { Text(option) }, enabled = option != entity?.state,
                        onClick = { open = false; select(option) })
                }
            }
        }
    }
}

@Composable
internal fun NumberTile(
    label: String, entity: Entity?, pending: String?, enabled: Boolean,
    modifier: Modifier, accent: TileAccent?, step: (Boolean) -> Unit,
) {
    val value = entity?.numberValue
    val range = entity?.numberRange
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AccentedTileTitle(label, Icons.Rounded.Numbers, accent)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TileValue(pending ?: displayValue(entity), Modifier.weight(1f), maxLines = 2)
                FilledTonalIconButton(onClick = { step(false) },
                    enabled = enabled && value != null && range?.adjacent(value, false) != null) {
                    Icon(Icons.Rounded.Remove, "Decrease $label", Modifier.size(compactTileIconSize))
                }
                FilledTonalIconButton(onClick = { step(true) },
                    enabled = enabled && value != null && range?.adjacent(value, true) != null) {
                    Icon(Icons.Rounded.Add, "Increase $label", Modifier.size(compactTileIconSize))
                }
            }
        }
    }
}

@Composable
internal fun ConfirmTileAction(label: String, enabled: Boolean, dismiss: () -> Unit, run: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Run $label?") },
        confirmButton = { Button(onClick = run, enabled = enabled) { Text("Run") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
