package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import app.flickpanel.config.badgeStateLabel
import app.flickpanel.config.badgeStates
import app.flickpanel.ha.Entity

@Composable
internal fun WarningBadgeSettings(
    badges: List<HomeBadge>, state: PanelUi, add: () -> Unit,
    edit: (HomeBadge) -> Unit, remove: (HomeBadge) -> Unit,
) {
    FilledTonalButton(onClick = add) {
        Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
        Text("Add alert")
    }
    for (badge in badges) {
        val name = badge.label ?: state.entity(badge.entity)?.name ?: badge.entity
        SettingLink(name, badge.activeStates.joinToString(", ", transform = ::badgeStateLabel),
            { edit(badge) }, clear = { remove(badge) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WarningBadgeEditor(badge: HomeBadge, state: PanelUi, onInput: () -> Unit, apply: (HomeBadge) -> Unit) {
    val entity = state.entity(badge.entity)
    var label by remember(badge) { mutableStateOf(badge.label.orEmpty()) }
    var states by remember(badge) { mutableStateOf(badge.activeStates.joinToString(", ")) }
    val active = remember(states) { badgeStates(states.split(',')) }
    val options = remember(entity) { entity?.alertOptions().orEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(setupPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(entity?.name ?: badge.entity, style = MaterialTheme.typography.titleLarge)
        if (entity?.available == true && entity.domain == "sensor") {
            Text("Current state · ${entity.state}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(label, {
            label = it.take(48)
            onInput()
        }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Badge label") }, placeholder = { Text(entity?.name ?: badge.entity) })
        if (options.isNotEmpty() && active.all(options::contains)) {
            Text("Show when", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in options) {
                    val selected = option in active
                    FilterChip(selected = selected, onClick = {
                        states = (if (selected) active - option else active + option).joinToString(", ")
                        onInput()
                    }, label = { Text(badgeStateLabel(option)) })
                }
            }
        } else {
            OutlinedTextField(states, {
                states = it.take(512)
                onInput()
            }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Show for these states") },
                supportingText = { Text("Separate multiple states with commas, for example high, very-high.") })
        }
        Button(enabled = active.isNotEmpty(), onClick = {
            apply(badge.copy(label = label.trim().takeIf(String::isNotEmpty), activeStates = active))
        }) { Text("Apply badge") }
    }
}

private fun Entity.alertOptions(): List<String> {
    if (domain == "binary_sensor" || domain == "input_boolean") return listOf("on", "off")
    val options = attributes.optJSONArray("options") ?: return emptyList()
    if (options.length() > 12) return emptyList()
    return badgeStates((0 until options.length()).mapNotNull { options.opt(it) as? String })
}
