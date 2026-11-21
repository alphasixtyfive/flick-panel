package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
internal fun PlayerVolumeControl(
    volume: SpeakerVolume, modifier: Modifier = Modifier, enabled: Boolean = true, vertical: Boolean = false,
) {
    val level = volume.level
    val ready = enabled && volume.enabled
    val colors = MaterialTheme.colorScheme
    val down: @Composable () -> Unit = {
        IconButton(
            onClick = { volume.change((level - speakerVolumeStep).coerceAtLeast(0)) },
            enabled = ready && level > 0, modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.Remove, "Lower speaker volume", Modifier.size(28.dp))
        }
    }
    val up: @Composable () -> Unit = {
        IconButton(
            onClick = { volume.change((level + speakerVolumeStep).coerceAtMost(100)) },
            enabled = ready && level < 100, modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.Add, "Raise speaker volume", Modifier.size(28.dp))
        }
    }
    val speaker: @Composable () -> Unit = {
        Icon(
            if (level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            "Speaker volume", Modifier.size(21.dp).semantics { stateDescription = "$level percent" },
            tint = colors.onSurfaceVariant,
        )
    }
    Surface(modifier, shape = CircleShape, color = colors.surfaceContainerHighest, contentColor = colors.onSurface) {
        if (vertical) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                up()
                speaker()
                down()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                down()
                speaker()
                up()
            }
        }
    }
}
