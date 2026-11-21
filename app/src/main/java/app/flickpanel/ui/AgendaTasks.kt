package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.flickpanel.AgendaState
import app.flickpanel.ha.AgendaTask
import app.flickpanel.ha.MAX_TASK_TITLE_LENGTH
import app.flickpanel.ha.normalizedTaskTitle

@Composable
internal fun AddTaskField(state: AgendaState, ready: Boolean, add: (String) -> Boolean, close: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    var submittedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = normalizedTaskTitle(draft)
    val submit = {
        if (ready && valid != null && add(valid)) submittedAt = state.savedRevision
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.saving, state.savedRevision) {
        val revision = submittedAt
        if (!state.saving && revision != null) {
            submittedAt = null
            if (revision != state.savedRevision) {
                draft = ""
                keyboard?.hide()
                close()
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(draft, { draft = it.take(MAX_TASK_TITLE_LENGTH) }, Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true, enabled = !state.saving, label = { Text("New task") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                draft = ""
                keyboard?.hide()
                close()
            }, enabled = !state.saving) { Text("Cancel") }
            Button(onClick = submit, enabled = ready && valid != null) { Text("Add") }
        }
    }
}

internal fun LazyListScope.taskRows(
    tasks: List<AgendaTask>, dates: AgendaDates, canComplete: Boolean, ready: Boolean,
    showCompleted: Boolean, toggleCompleted: () -> Unit, complete: (String, Boolean) -> Unit,
) {
    items(tasks.filterNot(AgendaTask::completed), key = { "open:${it.uid}" }) { task ->
        TaskRow(task, dates, canComplete, ready) { complete(task.uid, it) }
    }
    val completed = tasks.filter(AgendaTask::completed)
    if (completed.isNotEmpty()) {
        item(key = "completed-header") {
            Surface(onClick = toggleCompleted, shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics {
                stateDescription = if (showCompleted) "Expanded" else "Collapsed"
            }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Completed (${completed.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (showCompleted) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                        Modifier.size(compactTileIconSize))
                }
            }
        }
        if (showCompleted) {
            items(completed, key = { "completed:${it.uid}" }) { task ->
                TaskRow(task, dates, canComplete, ready) { complete(task.uid, it) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: AgendaTask, dates: AgendaDates, writable: Boolean, ready: Boolean, change: (Boolean) -> Unit) {
    val interaction = if (writable) Modifier.toggleable(task.completed, enabled = ready, role = Role.Checkbox, onValueChange = change)
    else Modifier.semantics(mergeDescendants = true) { stateDescription = if (task.completed) "Completed" else "Open" }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(interaction).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = task.completed, onCheckedChange = null, enabled = writable && ready)
            Column(Modifier.weight(1f)) {
                Text(task.summary, style = MaterialTheme.typography.titleLarge)
                if (!task.completed) task.due?.let { date -> AgendaSubtitle(dates.dueText(date)) }
            }
        }
    }
}
