package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelTheme
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelRemoteState

internal enum class PanelPage(val label: String) {
    DISPLAY("Display"), APPEARANCE("Appearance"), HOME_ASSISTANT("Home Assistant"), REMOTE_CONTROL("Remote control"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelSettings(
    page: PanelPage?, draft: PanelConfig, state: PanelUi, open: (PanelPage) -> Unit, change: (PanelConfig) -> Unit,
    choosePhotos: () -> Unit, chooseMessage: () -> Unit, choosePresence: () -> Unit, changeConnection: () -> Unit,
    linkPanel: (String) -> Unit, unlinkPanel: () -> Unit,
) {
    val context = LocalContext.current
    val album = draft.idleAlbum?.title ?: "Clock only"
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = setupPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (page) {
            null -> {
                SettingLink("Display", "$album · ${idleDelay(draft.idleSeconds)}", { open(PanelPage.DISPLAY) }, Icons.Rounded.DisplaySettings)
                SettingLink("Appearance", draft.theme.label, { open(PanelPage.APPEARANCE) }, Icons.Rounded.Palette)
                val connection = when (state.link) {
                    Link.ONLINE -> "Connected"
                    Link.CONNECTING -> "Connecting…"
                    Link.AUTH_REJECTED -> "Sign-in required"
                    Link.OFFLINE -> "Offline"
                }
                SettingLink("Home Assistant", connection, { open(PanelPage.HOME_ASSISTANT) }, Icons.Rounded.Hub)
                SettingLink("Android Home app", "Return from other apps with the Home gesture", { openHomeAppSettings(context) }, Icons.Rounded.Home)
            }
            PanelPage.DISPLAY -> {
                SettingLink("Idle photos", album, choosePhotos, clear = draft.idleAlbum?.let { { change(draft.copy(idleAlbum = null)) } })
                var open by remember { mutableStateOf(false) }
                Box {
                    SettingLink("Show idle screen after", idleDelay(draft.idleSeconds), { open = true }, Icons.Rounded.Schedule)
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        for (seconds in (listOf(0, 15, 30, 60, 120, 300, 600, 900) + draft.idleSeconds).distinct().sorted()) {
                            DropdownMenuItem(
                                text = { Text(idleDelay(seconds)) },
                                leadingIcon = { RadioButton(selected = draft.idleSeconds == seconds, onClick = null) },
                                onClick = { open = false; change(draft.copy(idleSeconds = seconds)) },
                            )
                        }
                    }
                }
                SetupSwitchRow("Wall display", "Keep the display on and dim the idle screen", draft.wallMode) {
                    change(draft.copy(wallMode = it))
                }
            }
            PanelPage.APPEARANCE -> {
                Text("Colour scheme", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.titleMedium)
                FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (theme in PanelTheme.entries) {
                        ThemeChoice(theme, draft.theme == theme, draft.highContrast, Modifier.weight(1f)) {
                            change(draft.copy(theme = theme))
                        }
                    }
                }
                SetupSwitchRow("Higher contrast", "Stronger text and control outlines", draft.highContrast) {
                    change(draft.copy(highContrast = it))
                }
                SetupSwitchRow("Reduce motion", "Skip page fades and tile movement", draft.reduceMotion) {
                    change(draft.copy(reduceMotion = it))
                }
            }
            PanelPage.HOME_ASSISTANT -> {
                SettingLink("Remote control", state.remoteLabel(), { open(PanelPage.REMOTE_CONTROL) }, Icons.Rounded.SettingsRemote)
                SettingLink("Home message", draft.messageEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", chooseMessage,
                    clear = draft.messageEntity?.let { { change(draft.copy(messageEntity = null)) } })
                SettingLink("Wake on approach", draft.presenceEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", choosePresence,
                    clear = draft.presenceEntity?.let { { change(draft.copy(presenceEntity = null)) } })
                SettingLink("Home Assistant connection", state.serverAddress, changeConnection, Icons.Rounded.Hub)
            }
            PanelPage.REMOTE_CONTROL -> RemoteControlSettings(state, linkPanel, unlinkPanel)
        }
    }
}

@Composable
private fun RemoteControlSettings(state: PanelUi, link: (String) -> Unit, unlink: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val linkedName = state.remoteName
    var name by remember { mutableStateOf("Flick Panel") }
    var confirmUnlink by remember { mutableStateOf(false) }
    if (linkedName == null) {
        Text("Link this tablet to blank or wake Panel and open its cameras from Home Assistant.",
            color = colors.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(48) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = !state.busy, label = { Text("Tablet name") })
        Button(onClick = { link(name) }, enabled = !state.busy && state.link == Link.ONLINE && name.isNotBlank()) {
            Text(if (state.busy) "Linking…" else "Link tablet")
        }
    } else {
        Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
            ListItem(headlineContent = { Text(linkedName, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(state.remoteLabel()) },
                leadingContent = { Icon(Icons.Rounded.SettingsRemote, null, tint = colors.primary) },
                colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer))
        }
        Text("Send commands to the tablet’s Mobile App notification action in Home Assistant.",
            color = colors.onSurfaceVariant)
        Text("Panel stays reachable while its display is blank. A tap wakes it locally.",
            color = colors.onSurfaceVariant)
        if (state.remoteState == PanelRemoteState.UNAVAILABLE) {
            TextButton(onClick = { link(linkedName) }, enabled = !state.busy && state.link == Link.ONLINE) {
                Text("Relink tablet")
            }
        }
        TextButton(onClick = { confirmUnlink = true }, enabled = !state.busy) { Text("Unlink tablet") }
    }
    if (confirmUnlink) AlertDialog(
        onDismissRequest = { confirmUnlink = false },
        title = { Text("Unlink this tablet?") },
        text = { Text("You can remove its Mobile App entry in Home Assistant after unlinking.") },
        confirmButton = { TextButton(onClick = { confirmUnlink = false; unlink() }) { Text("Unlink") } },
        dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancel") } },
    )
}

private fun PanelUi.remoteLabel(): String = if (remoteName == null) "Not linked" else when (remoteState) {
    PanelRemoteState.READY -> "Ready"
    PanelRemoteState.UNAVAILABLE -> "Unavailable"
    PanelRemoteState.CONNECTING, PanelRemoteState.DISABLED -> "Connecting…"
}

private fun idleDelay(seconds: Int) = when {
    seconds == 0 -> "Never automatically"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds sec"
}

@Composable
private fun ThemeChoice(theme: PanelTheme, selected: Boolean, contrast: Boolean, modifier: Modifier, choose: () -> Unit) {
    val sample = panelColorScheme(theme, contrast)
    val colors = MaterialTheme.colorScheme
    Surface(selected = selected, onClick = choose, modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface) {
        Column(Modifier.padding(start = 13.dp, end = 10.dp, top = 13.dp, bottom = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (swatch in listOf(sample.surfaceContainerHighest, sample.primaryContainer, sample.primary)) {
                    Box(Modifier.size(20.dp).background(swatch, CircleShape))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

@Composable
internal fun SetupSwitchRow(title: String, subtitle: String?, checked: Boolean, change: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        ListItem(
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = change),
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = subtitle?.let { { Text(it) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
        )
    }
}

@Composable
internal fun SettingLink(title: String, subtitle: String?, open: () -> Unit, icon: ImageVector? = null, clear: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = open, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
                ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = subtitle?.let { { Text(it) } },
                    leadingContent = icon?.let { { Icon(it, null, tint = colors.primary) } },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer))
            }
            if (clear != null) IconButton(onClick = clear) { Icon(Icons.Rounded.Close, "Clear $title") }
        }
    }
}
