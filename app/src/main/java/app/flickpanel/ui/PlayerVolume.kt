package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { volume.change((level - speakerVolumeStep).coerceAtLeast(0)) },
            enabled = ready && level > 0, modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.Remove, "Lower speaker volume")
        }
        Text("$level%", Modifier.widthIn(min = 44.dp).padding(horizontal = 2.dp).semantics {
            contentDescription = "Speaker volume"
            stateDescription = "$level percent"
        }, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "'tnum'"),
            maxLines = 1, textAlign = TextAlign.Center)
        IconButton(
            onClick = { volume.change((level + speakerVolumeStep).coerceAtMost(100)) },
            enabled = ready && level < 100, modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.Add, "Raise speaker volume")
        }
    }
}
