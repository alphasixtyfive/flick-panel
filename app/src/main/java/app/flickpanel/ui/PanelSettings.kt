package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.rounded.SettingsRemote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelTheme
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelRemoteState

internal enum class PanelPage(val label: String) {
    DISPLAY("Display"), APPEARANCE("Appearance"), HOME_ASSISTANT("Home Assistant"), REMOTE_CONTROL("Remote control"),
    HOME_BADGES("Warning badges"),
}

@Composable
internal fun PanelSettings(
    page: PanelPage?, draft: PanelConfig, state: PanelUi, open: (PanelPage) -> Unit, change: (PanelConfig) -> Unit,
    choosePhotos: () -> Unit, chooseMessage: () -> Unit, choosePresence: () -> Unit, changeConnection: () -> Unit,
    linkPanel: (String) -> Unit, unlinkPanel: () -> Unit,
    chooseBadge: () -> Unit, editBadge: (HomeBadge) -> Unit,
) {
    val context = LocalContext.current
    val album = draft.idleAlbum?.title ?: "Clock only"
    Column(
        Modifier.widthIn(max = 780.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = setupPadding, end = setupPadding, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (page) {
            null -> SettingsListGroup {
                SettingLink("Display", "$album · ${idleDelay(draft.idleSeconds)}",
                    { open(PanelPage.DISPLAY) }, Icons.Rounded.DisplaySettings)
                SettingLink("Appearance", draft.theme.label, { open(PanelPage.APPEARANCE) }, Icons.Rounded.Palette)
                val connection = when (state.link) {
                    Link.ONLINE -> "Connected"
                    Link.CONNECTING -> "Connecting…"
                    Link.AUTH_REJECTED -> "Sign-in required"
                    Link.OFFLINE -> "Offline"
                }
                SettingLink("Home Assistant", connection, { open(PanelPage.HOME_ASSISTANT) }, Icons.Rounded.Hub)
                SettingLink("Android Home app", "Return from other apps with the Home gesture",
                    { openHomeAppSettings(context) }, Icons.Rounded.Home)
            }
            PanelPage.DISPLAY -> SettingsListGroup {
                SettingLink("Idle photos", album, choosePhotos,
                    clear = draft.idleAlbum?.let { { change(draft.copy(idleAlbum = null)) } })
                IdleDelaySetting(draft.idleSeconds) { change(draft.copy(idleSeconds = it)) }
                SetupSwitchRow("Wall display", "Keep the display on and dim the idle screen", draft.wallMode) {
                    change(draft.copy(wallMode = it))
                }
            }
            PanelPage.APPEARANCE -> {
                SettingsListGroup("Colour scheme") {
                    Column(Modifier.selectableGroup()) {
                        for (theme in PanelTheme.entries) {
                            ThemeChoice(theme, draft.theme == theme, draft.highContrast) {
                                change(draft.copy(theme = theme))
                            }
                        }
                    }
                }
                SettingsListGroup("Accessibility") {
                    SetupSwitchRow("Higher contrast", "Stronger text and control outlines", draft.highContrast) {
                        change(draft.copy(highContrast = it))
                    }
                    SetupSwitchRow("Reduce motion", "Skip page fades and tile movement", draft.reduceMotion) {
                        change(draft.copy(reduceMotion = it))
                    }
                }
            }
            PanelPage.HOME_ASSISTANT -> {
                SettingsListGroup {
                    SettingLink("Home Assistant connection", state.serverAddress, changeConnection)
                    SettingLink("Remote control", state.remoteLabel(), { open(PanelPage.REMOTE_CONTROL) })
                }
                SettingsListGroup("Favourites") {
                    SettingLink("Home message", draft.messageEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", chooseMessage,
                        clear = draft.messageEntity?.let { { change(draft.copy(messageEntity = null)) } })
                    SettingLink("Warning badges", if (draft.homeBadges.isEmpty()) "Show active alerts on Home" else null,
                        { open(PanelPage.HOME_BADGES) })
                    SettingLink("Wake on approach", draft.presenceEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", choosePresence,
                        clear = draft.presenceEntity?.let { { change(draft.copy(presenceEntity = null)) } })
                }
            }
            PanelPage.REMOTE_CONTROL -> RemoteControlSettings(state, linkPanel, unlinkPanel)
            PanelPage.HOME_BADGES -> WarningBadgeSettings(draft.homeBadges, state, chooseBadge, editBadge) { removed ->
                change(draft.copy(homeBadges = draft.homeBadges - removed))
            }
        }
    }
}

@Composable
private fun SettingsListGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(
            title, Modifier.padding(start = 16.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
        )
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(content = content)
        }
    }
}

@Composable
private fun IdleDelaySetting(current: Int, change: (Int) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    SettingLink("Show idle screen after", idleDelay(current), { choosing = true })
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("Show idle screen after") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                val choices = (listOf(0, 15, 30, 60, 120, 300, 600, 900) + current).distinct().sorted()
                for (seconds in choices) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = seconds == current,
                            role = Role.RadioButton,
                            onClick = { choosing = false; change(seconds) },
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == seconds, onClick = null)
                        Text(idleDelay(seconds), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RemoteControlSettings(state: PanelUi, link: (String) -> Unit, unlink: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val linkedName = state.remoteName
    var name by remember { mutableStateOf("Flick Panel") }
    var confirmUnlink by remember { mutableStateOf(false) }
    if (linkedName == null) {
        Text("Link this tablet to wake the display or open a camera from Home Assistant.",
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(name, { name = it.take(48) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = !state.busy, label = { Text("Tablet name") })
        Button(onClick = { link(name) }, enabled = !state.busy && state.link == Link.ONLINE && name.isNotBlank()) {
            Text(if (state.busy) "Linking…" else "Link tablet")
        }
    } else {
        SettingsListGroup {
            ListItem(
                headlineContent = { Text(linkedName) },
                supportingContent = { Text(state.remoteLabel()) },
                leadingContent = { Icon(Icons.Rounded.SettingsRemote, null, tint = colors.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Text("Use the tablet’s Mobile App notification action in Home Assistant to send commands.",
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        if (state.remoteState == PanelRemoteState.UNAVAILABLE) {
            Button(onClick = { link(linkedName) }, enabled = !state.busy && state.link == Link.ONLINE) {
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
private fun ThemeChoice(theme: PanelTheme, selected: Boolean, contrast: Boolean, choose: () -> Unit) {
    val sample = panelColorScheme(theme, contrast)
    ListItem(
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = choose),
        headlineContent = { Text(theme.label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (swatch in listOf(sample.surfaceContainerHighest, sample.primaryContainer, sample.primary)) {
                        Box(Modifier.size(14.dp).background(swatch, CircleShape))
                    }
                }
                RadioButton(selected = selected, onClick = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SetupSwitchRow(title: String, subtitle: String?, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = change),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingLink(title: String, subtitle: String?, open: () -> Unit, icon: ImageVector? = null, clear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ListItem(
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = open),
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.primary) } },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (clear != null) IconButton(onClick = clear) { Icon(Icons.Rounded.Close, "Clear $title") }
    }
}
