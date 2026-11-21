package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PlayerVolumeButton(id: String, level: Int, available: Boolean, ready: Boolean, change: (Int) -> Unit) {
    var expanded by remember(id, available) { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }, enabled = available, modifier = Modifier.size(48.dp)
            .semantics { stateDescription = "$level percent" }) {
            val icon = if (level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
            Icon(icon, "Adjust speaker volume")
        }
        if (expanded) {
            DropdownMenu(expanded = true, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 270.dp)) {
                PlayerVolumeControl(level, ready, change)
            }
        }
    }
}

@Composable
internal fun PlayerVolumeControl(level: Int, ready: Boolean, change: (Int) -> Unit) {
    var drag by remember(ready) { mutableStateOf<Float?>(null) }
    val shown = (drag ?: level.toFloat()).roundToInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Speaker volume", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$shown%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = drag ?: level.toFloat(),
            onValueChange = { drag = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Speaker volume" },
            enabled = ready,
            valueRange = 0f..100f,
            onValueChangeFinished = {
                val next = drag?.roundToInt()
                drag = null
                if (next != null && next != level) change(next)
            },
        )
    }
}
