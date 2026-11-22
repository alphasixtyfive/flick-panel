package app.flickpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.CameraAction
import app.flickpanel.config.CameraActionIcon
import app.flickpanel.config.cameraActionLabelLimit
import app.flickpanel.config.cameraActionLimit
import app.flickpanel.config.defaultCameraActionLabel
import app.flickpanel.ha.Entity
import app.flickpanel.ha.cameraActionKind

@Composable
internal fun CameraActionsEditor(actions: List<CameraAction>, state: PanelUi, change: (List<CameraAction>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = actions.firstOrNull { it.entity == editingId }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Camera actions", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            if (actions.size < cameraActionLimit) TextButton(onClick = { adding = true }) { Text("Add action") }
        }
        for ((index, action) in actions.withIndex()) key(action.entity) {
            val entityName = state.entity(action.entity)?.name ?: readableEntityName(action.entity)
            val name = action.label ?: defaultCameraActionLabel(action.entity, entityName)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SettingLink(name, entityName.takeUnless { it == name }, { editingId = action.entity })
                }
                if (actions.size > 1) {
                    ReorderGrip(name, before = index > 0, after = index < actions.lastIndex) { direction ->
                        val target = index + direction
                        if (target in actions.indices) {
                            change(actions.toMutableList().apply { add(target, removeAt(index)) })
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val options = remember(state.catalogue, actions, editing) {
            val used = actions.filterNot { it == editing }.mapTo(hashSetOf(), CameraAction::entity)
            state.catalogue.entries.asSequence().filter { it.visible }.map { it.entity }
                .filter { it.id !in used && it.cameraActionKind != null }
                .sortedBy { it.name.lowercase() }.toList()
        }
        if (adding) {
            CameraActionPicker("Add camera action", options, dismiss = { adding = false }) { entity ->
                change(actions + CameraAction(entity))
                adding = false
            }
        } else if (editing != null) {
            CameraActionDialog(
                editing, state, options, dismiss = { editingId = null },
                remove = {
                    change(actions - editing)
                    editingId = null
                },
            ) { action ->
                change(actions.map { if (it.entity == editing.entity) action else it })
                editingId = null
            }
        }
    }
}

@Composable
private fun CameraActionPicker(
    title: String, options: List<Entity>, dismiss: () -> Unit, choose: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(options, query) {
        val term = query.trim()
        options.filter { it.name.contains(term, ignoreCase = true) || it.id.contains(term, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupSearchField(query, { query = it }, "Find a device or action", Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 248.dp).weight(1f, fill = false)) {
                    if (matches.isEmpty()) item {
                        ListItem(headlineContent = {
                            Text(if (options.isEmpty()) "No camera actions available" else "No matches")
                        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    items(matches, key = Entity::id) { option ->
                        ListItem(
                            headlineContent = { Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(option.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable(role = Role.Button) { choose(option.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CameraActionDialog(
    editing: CameraAction, state: PanelUi, options: List<Entity>, dismiss: () -> Unit,
    remove: () -> Unit, save: (CameraAction) -> Unit,
) {
    var selected by remember(editing) { mutableStateOf(editing.entity) }
    var label by remember(editing) { mutableStateOf(editing.label.orEmpty()) }
    var choosing by remember(editing) { mutableStateOf(false) }
    var icon by remember(editing) { mutableStateOf(editing.icon) }
    var choosingIcon by remember(editing) { mutableStateOf(false) }
    if (choosing) {
        CameraActionPicker("Change entity", options, dismiss = { choosing = false }) { entity ->
            selected = entity
            choosing = false
        }
        return
    }
    val entityName = state.entity(selected)?.name ?: readableEntityName(selected)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit camera action") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingLink("Entity", entityName, { choosing = true })
                OutlinedTextField(
                    label, { label = it.take(cameraActionLabelLimit) }, Modifier.fillMaxWidth(),
                    label = { Text("Button name") }, placeholder = { Text(defaultCameraActionLabel(selected, entityName)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Box {
                    SettingLink("Icon", icon.label, { choosingIcon = true }, icon = cameraActionSymbol(icon, selected))
                    DropdownMenu(choosingIcon, onDismissRequest = { choosingIcon = false }) {
                        for (option in CameraActionIcon.entries) {
                            val symbol = cameraActionSymbol(option, selected)
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { icon = option; choosingIcon = false },
                                leadingIcon = symbol?.let { { Icon(it, null) } },
                            )
                        }
                    }
                }
                TextButton(onClick = remove) { Text("Remove action", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = { save(CameraAction(selected, label.trim().takeIf(String::isNotEmpty), icon)) }) { Text("Save") }
        },
    )
}

private fun readableEntityName(id: String): String =
    id.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
