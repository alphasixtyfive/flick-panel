package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PlayerVolumeButton(level: Int, selected: Boolean, enabled: Boolean, toggle: () -> Unit) {
    FilledTonalIconToggleButton(
        checked = selected,
        onCheckedChange = { toggle() },
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { stateDescription = "$level percent" },
    ) {
        val icon = if (level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
        Icon(icon, if (selected) "Close volume" else "Adjust speaker volume")
    }
}

@Composable
internal fun PlayerVolumeControl(level: Int, ready: Boolean, change: (Int) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember(ready) { mutableStateOf<Float?>(null) }
    val position = drag ?: level / 100f
    val shown = (position * 100).roundToInt()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    Column(modifier) {
        MediaSlider(position, "Speaker volume", ready, change = { drag = it }) {
            val next = drag?.let { (it * 100).roundToInt() }
            drag = null
            if (next != null && next != level) change(next)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speaker volume", style = style, color = color)
            Text("$shown%", style = style.copy(fontFeatureSettings = "'tnum'"), color = color)
        }
    }
}
