package app.flickpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsListGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(
            title, Modifier.padding(start = 16.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
        )
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SetupSwitchRow(title: String, subtitle: String?, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = change).heightIn(min = 64.dp),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingLink(
    title: String, subtitle: String?, open: () -> Unit, icon: ImageVector? = null,
    clear: (() -> Unit)? = null, external: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ListItem(
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = open).heightIn(min = 64.dp),
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.primary) } },
            trailingContent = if (external) {
                { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Opens in another app") }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (clear != null) IconButton(onClick = clear) { Icon(Icons.Rounded.Close, "Clear $title") }
    }
}

@Composable
internal fun <T> SettingChoices(
    options: List<T>, current: T, label: (T) -> String, change: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        for (option in options) {
            val selected = current == option
            ListItem(
                modifier = Modifier.selectable(selected, role = Role.RadioButton) {
                    if (!selected) change(option)
                }.heightIn(min = 56.dp),
                headlineContent = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                trailingContent = { RadioButton(selected, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
internal fun TileSizePicker(wide: Boolean, change: (Boolean) -> Unit) {
    SettingsListGroup("Tile size") {
        SettingChoices(listOf(false, true), wide, { if (it) "Wide" else "Standard" }, change)
    }
}
