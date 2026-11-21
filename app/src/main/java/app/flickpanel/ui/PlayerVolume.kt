package app.flickpanel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class SpeakerVolume(
    val level: Int,
    val muted: Boolean?,
    val ready: Boolean,
    val pending: Boolean,
    val change: (Int) -> Unit,
    val mute: (Boolean) -> Unit,
)

@Composable
internal fun PlayerVolumeControl(volume: SpeakerVolume, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ready = enabled && volume.ready
    var preview by remember { mutableStateOf<Float?>(null) }
    var requested by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready, volume.pending) {
        if (!ready) preview = null
        if (!volume.pending) requested = null
    }
    val level = preview ?: requested?.takeIf { volume.pending }?.toFloat() ?: volume.level.toFloat()
    val icon = if (volume.muted == true || level == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (volume.muted != null) {
            IconButton(onClick = { volume.mute(!volume.muted) }, enabled = ready, modifier = Modifier.size(48.dp)) {
                Icon(icon, if (volume.muted) "Unmute speaker" else "Mute speaker")
            }
        } else {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.CenterStart) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MediaSlider(
            value = level / 100f,
            label = "Speaker volume",
            enabled = ready,
            change = { preview = it * 100f },
            finish = {
                val next = preview?.roundToInt()
                preview = null
                if (ready && next != null && next != volume.level) {
                    requested = next
                    volume.change(next)
                }
            },
            modifier = Modifier.weight(1f),
            stateLabel = "${level.roundToInt()} percent",
        )
    }
}
