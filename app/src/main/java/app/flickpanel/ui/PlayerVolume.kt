package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

private const val speakerVolumeStep = 5

internal data class SpeakerVolume(
    val level: Int,
    val enabled: Boolean,
    val change: (Int) -> Unit,
)

@Composable
internal fun PlayerVolumeControl(volume: SpeakerVolume, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val level = volume.level
    val ready = enabled && volume.enabled
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { volume.change((level - speakerVolumeStep).coerceAtLeast(0)) },
            enabled = ready && level > 0, modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.Remove, "Lower speaker volume", Modifier.size(26.dp))
        }
        Text(
            "Volume", Modifier.semantics { stateDescription = "$level percent" },
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        FilledTonalIconButton(
            onClick = { volume.change((level + speakerVolumeStep).coerceAtMost(100)) },
            enabled = ready && level < 100, modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.Add, "Raise speaker volume", Modifier.size(26.dp))
        }
    }
}
