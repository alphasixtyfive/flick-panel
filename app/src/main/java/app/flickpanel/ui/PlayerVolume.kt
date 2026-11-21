package app.flickpanel.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    val colors = MaterialTheme.colorScheme
    val target = Modifier.size(width = 56.dp, height = 48.dp).semantics { stateDescription = "$level percent" }
    Surface(modifier, shape = CircleShape, color = colors.surfaceContainerHighest, contentColor = colors.onSurface) {
        Row {
            IconButton(
                onClick = { volume.change((level - speakerVolumeStep).coerceAtLeast(0)) },
                enabled = ready && level > 0, modifier = target,
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, "Lower speaker volume", Modifier.size(26.dp))
            }
            IconButton(
                onClick = { volume.change((level + speakerVolumeStep).coerceAtMost(100)) },
                enabled = ready && level < 100, modifier = target,
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Raise speaker volume", Modifier.size(26.dp))
            }
        }
    }
}
