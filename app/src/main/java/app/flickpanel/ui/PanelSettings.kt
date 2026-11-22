package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelBackupAction
import app.flickpanel.config.PanelTheme
import app.flickpanel.ha.Link
import kotlin.math.roundToInt

internal enum class PanelPage(val label: String, val readOnly: Boolean = false) {
    DISPLAY("Display"),
    SCREENSAVER_TIMEOUT("Screensaver timeout"),
    APPEARANCE("Appearance"),
    HOME_ASSISTANT("Home Assistant"),
    REMOTE_CONTROL("Tablet link"),
    HOME_BADGES("Home alerts"),
    BACKUP("Backup & restore"),
    ABOUT("About", readOnly = true),
    FONT_LICENCE("Google Sans Flex licence", readOnly = true),
}

@Composable
internal fun PanelSettings(
    page: PanelPage?, draft: PanelConfig, state: PanelUi, open: (PanelPage) -> Unit, change: (PanelConfig) -> Unit,
    choosePhotos: () -> Unit, chooseMessage: () -> Unit, changeConnection: () -> Unit,
    linkPanel: (String) -> Unit, unlinkPanel: () -> Unit,
    chooseBadge: () -> Unit, editBadge: (HomeBadge) -> Unit,
    backup: (PanelBackupAction) -> Unit,
) {
    val context = LocalContext.current
    val album = draft.idleAlbum?.title ?: "Clock only"
    val scroll = key(page) { rememberScrollState() }
    var confirmRestore by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(scroll)
            .padding(start = setupPadding, end = setupPadding, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (page) {
            null -> {
                SettingsListGroup {
                    SettingLink(PanelPage.DISPLAY.label, "Brightness and screensaver",
                        { open(PanelPage.DISPLAY) }, Icons.Rounded.DisplaySettings)
                    SettingLink(PanelPage.APPEARANCE.label, draft.theme.label,
                        { open(PanelPage.APPEARANCE) }, Icons.Rounded.Palette)
                    SettingLink("Android Home app", "Return to Flick Panel with the Home gesture",
                        { openHomeAppSettings(context) }, Icons.Rounded.Home, external = true)
                }
                SettingsListGroup {
                    val connection = when (state.link) {
                        Link.ONLINE -> "Connected"
                        Link.CONNECTING -> "Connecting…"
                        Link.AUTH_REJECTED -> "Sign-in required"
                        Link.OFFLINE -> "Offline"
                    }
                    SettingLink(PanelPage.HOME_ASSISTANT.label, connection, { open(PanelPage.HOME_ASSISTANT) }, Icons.Rounded.Hub)
                }
                SettingsListGroup {
                    SettingLink(PanelPage.BACKUP.label, null, { open(PanelPage.BACKUP) }, Icons.Rounded.Backup)
                    SettingLink(PanelPage.ABOUT.label, null, { open(PanelPage.ABOUT) }, Icons.Rounded.Info)
                }
            }
            PanelPage.DISPLAY -> {
                SettingsListGroup {
                    BrightnessSetting("Dashboard brightness", draft.dashboardBrightness, "Follow Android", 50) {
                        change(draft.copy(dashboardBrightness = it))
                    }
                    SetupSwitchRow("Wall display", "Keep the screen on while Flick Panel is open", draft.wallMode) {
                        change(draft.copy(wallMode = it))
                    }
                }
                SettingsListGroup("Screensaver") {
                    SettingLink("Idle photos", album, choosePhotos,
                        clear = draft.idleAlbum?.let { { change(draft.copy(idleAlbum = null)) } })
                    SettingLink(PanelPage.SCREENSAVER_TIMEOUT.label, idleDelay(draft.idleSeconds),
                        { open(PanelPage.SCREENSAVER_TIMEOUT) })
                    BrightnessSetting(
                        "Screensaver brightness", draft.screensaverBrightness, "Automatic", draft.defaultScreensaverBrightness,
                    ) { change(draft.copy(screensaverBrightness = it)) }
                }
            }
            PanelPage.SCREENSAVER_TIMEOUT -> SettingsListGroup {
                val choices = (listOf(0, 15, 30, 60, 120, 300, 600, 900) + draft.idleSeconds).distinct().sorted()
                SettingChoices(choices, draft.idleSeconds, ::idleDelay) { change(draft.copy(idleSeconds = it)) }
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
                    SettingLink(PanelPage.REMOTE_CONTROL.label, state.remoteLabel(), { open(PanelPage.REMOTE_CONTROL) })
                }
                SettingsListGroup("Favourites") {
                    SettingLink("Home message", draft.messageEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", chooseMessage,
                        clear = draft.messageEntity?.let { { change(draft.copy(messageEntity = null)) } })
                    SettingLink(PanelPage.HOME_BADGES.label, if (draft.homeBadges.isEmpty()) "Show active alerts on Home" else null,
                        { open(PanelPage.HOME_BADGES) })
                }
            }
            PanelPage.REMOTE_CONTROL -> PanelLinkSettings(state, linkPanel, unlinkPanel)
            PanelPage.HOME_BADGES -> WarningBadgeSettings(draft.homeBadges, state, chooseBadge, editBadge) { removed ->
                change(draft.copy(homeBadges = draft.homeBadges - removed))
            }
            PanelPage.BACKUP -> {
                if (draft != state.config) {
                    Text("Save your changes before choosing a backup file.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    SettingsListGroup {
                        SettingLink("Back up", "Save your layout and panel preferences",
                            { backup(PanelBackupAction.BACK_UP) })
                        SettingLink("Restore", "Replace the layout from a Flick Panel backup",
                            { confirmRestore = true })
                    }
                    Text("Backups leave your setup PIN and Home Assistant connection on this device.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PanelPage.ABOUT -> PanelAbout { open(PanelPage.FONT_LICENCE) }
            PanelPage.FONT_LICENCE -> FontLicence()
        }
    }
    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Restore a backup?") },
        text = { Text("The selected file will replace the saved layout and panel preferences.") },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                confirmRestore = false
                backup(PanelBackupAction.RESTORE)
            }) { Text("Choose file") }
        },
    )
}

@Composable
private fun BrightnessSetting(title: String, current: Int?, defaultLabel: String, defaultLevel: Int, change: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(current?.let { "$it%" } ?: defaultLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = (current ?: defaultLevel).toFloat(),
                onValueChange = { change(it.roundToInt()) },
                valueRange = 1f..100f,
                modifier = Modifier.weight(1f).semantics { contentDescription = title },
            )
            TextButton(
                onClick = { change(null) },
                enabled = current != null,
                modifier = Modifier.semantics { contentDescription = "Reset ${title.lowercase()}" },
            ) { Text("Reset") }
        }
    }
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
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = choose).heightIn(min = 64.dp),
        headlineContent = { Text(theme.label, style = MaterialTheme.typography.titleMedium) },
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
