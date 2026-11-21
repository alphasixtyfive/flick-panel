package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelRemoteState

@Composable
internal fun PanelLinkSettings(state: PanelUi, link: (String) -> Unit, unlink: () -> Unit) {
    val device = state.remoteName
    var name by rememberSaveable { mutableStateOf("Flick Panel") }
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    if (device == null) {
        Text("Link this tablet to send display commands and see battery information in Home Assistant.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            name, { name = it.take(48) }, Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            label = { Text("Tablet name") }, singleLine = true, enabled = !state.busy,
        )
        Button(onClick = { link(name) }, enabled = !state.busy && state.link == Link.ONLINE && name.isNotBlank()) {
            Text(if (state.busy) "Linking…" else "Link tablet")
        }
        return
    }

    SettingsListGroup {
        LinkDetail(device, state.remoteLabel(), Icons.Rounded.Hub)
    }
    SettingsListGroup("Home Assistant") {
        LinkDetail("Display controls", "Wake, blank, keep awake, adjust brightness or open a saved view",
            Icons.Rounded.DisplaySettings)
        LinkDetail("Tablet sensors", "Battery level, charging, charger, battery temperature and display mode",
            Icons.Rounded.BatteryChargingFull)
        LinkDetail("Automation action", "Use the tablet’s Mobile App notification action", Icons.Rounded.SettingsRemote)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.remoteState == PanelRemoteState.UNAVAILABLE) {
            Button(onClick = { link(device) }, enabled = !state.busy && state.link == Link.ONLINE) { Text("Relink tablet") }
        }
        TextButton(onClick = { confirmUnlink = true }, enabled = !state.busy) { Text("Unlink tablet") }
    }
    if (confirmUnlink) AlertDialog(
        onDismissRequest = { confirmUnlink = false },
        title = { Text("Unlink this tablet?") },
        text = { Text("This stops commands and sensor updates. You can remove the tablet’s Mobile App entry in Home Assistant.") },
        confirmButton = { TextButton(onClick = { confirmUnlink = false; unlink() }) { Text("Unlink") } },
        dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancel") } },
    )
}

@Composable
private fun LinkDetail(title: String, detail: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

internal fun PanelUi.remoteLabel(): String = if (remoteName == null) "Not linked" else when (remoteState) {
    PanelRemoteState.READY -> "Ready"
    PanelRemoteState.UNAVAILABLE -> "Unavailable"
    PanelRemoteState.CONNECTING, PanelRemoteState.DISABLED -> "Connecting…"
}
