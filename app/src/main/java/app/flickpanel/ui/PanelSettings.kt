package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelTheme
import app.flickpanel.ha.Link

internal enum class PanelPage(val label: String) {
    DISPLAY("Display"), APPEARANCE("Appearance"), HOME_ASSISTANT("Home Assistant"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelSettings(
    page: PanelPage?, draft: PanelConfig, state: PanelUi, open: (PanelPage) -> Unit, change: (PanelConfig) -> Unit,
    choosePhotos: () -> Unit, chooseMessage: () -> Unit, choosePresence: () -> Unit, changeConnection: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
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
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Show idle screen after", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (seconds in listOf(15, 30, 60, 120, 300, 600)) {
                                FilterChip(selected = draft.idleSeconds == seconds, onClick = { change(draft.copy(idleSeconds = seconds)) },
                                    label = { Text(idleDelay(seconds)) })
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                    ListItem(headlineContent = { Text("Wall display", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Keep the display on and dim the idle screen") },
                        trailingContent = { Switch(checked = draft.wallMode, onCheckedChange = { change(draft.copy(wallMode = it)) }) },
                        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer))
                }
            }
            PanelPage.APPEARANCE -> Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Colour scheme", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (theme in PanelTheme.entries) {
                            FilterChip(selected = draft.theme == theme, onClick = { change(draft.copy(theme = theme)) },
                                label = { Text(theme.label) })
                        }
                    }
                }
            }
            PanelPage.HOME_ASSISTANT -> {
                SettingLink("Home message", draft.messageEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", chooseMessage,
                    clear = draft.messageEntity?.let { { change(draft.copy(messageEntity = null)) } })
                SettingLink("Wake on approach", draft.presenceEntity?.let { state.entity(it)?.name ?: it } ?: "Choose entity", choosePresence,
                    clear = draft.presenceEntity?.let { { change(draft.copy(presenceEntity = null)) } })
                SettingLink("Change Home Assistant connection", null, changeConnection)
            }
        }
    }
}

private fun idleDelay(seconds: Int) = if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds sec"

@Composable
private fun SettingLink(title: String, subtitle: String?, open: () -> Unit, icon: ImageVector? = null, clear: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = open, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
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
