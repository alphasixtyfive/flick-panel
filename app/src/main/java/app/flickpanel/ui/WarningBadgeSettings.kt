package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.badgeStates

@Composable
internal fun WarningBadgeSettings(
    badges: List<HomeBadge>, state: PanelUi, add: () -> Unit,
    edit: (HomeBadge) -> Unit, remove: (HomeBadge) -> Unit,
) {
    FilledTonalButton(onClick = add) {
        Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
        Text("Add warning badge")
    }
    for (badge in badges) {
        val name = badge.label ?: state.entity(badge.entity)?.name ?: badge.entity
        SettingLink(name, badge.activeStates.joinToString(", "), { edit(badge) }, clear = { remove(badge) })
    }
}

@Composable
internal fun WarningBadgeEditor(badge: HomeBadge, state: PanelUi, apply: (HomeBadge) -> Unit) {
    val entity = state.entity(badge.entity)
    var label by remember(badge) { mutableStateOf(badge.label.orEmpty()) }
    var states by remember(badge) { mutableStateOf(badge.activeStates.joinToString(", ")) }
    val active = remember(states) { badgeStates(states.split(',')) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(setupPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(entity?.name ?: badge.entity, style = MaterialTheme.typography.titleLarge)
        if (entity?.available == true && entity.domain == "sensor") {
            Text("Current state · ${entity.state}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(label, { label = it.take(48) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Badge label") }, placeholder = { Text(entity?.name ?: badge.entity) })
        OutlinedTextField(states, { states = it.take(512) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Show for these states") },
            supportingText = { Text("Separate multiple states with commas, for example high, very-high.") })
        Button(enabled = active.isNotEmpty(), onClick = {
            apply(badge.copy(label = label.trim().takeIf(String::isNotEmpty), activeStates = active))
        }) { Text("Apply badge") }
    }
}
