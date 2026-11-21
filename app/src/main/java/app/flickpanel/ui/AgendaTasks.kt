package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.flickpanel.AgendaContent
import app.flickpanel.AgendaState
import app.flickpanel.ha.AgendaTask
import app.flickpanel.ha.MAX_TASK_TITLE_LENGTH
import app.flickpanel.ha.normalizedTaskTitle

@Composable
internal fun AddTaskDialog(state: AgendaState, ready: Boolean, add: (String) -> Boolean, close: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    var submittedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = normalizedTaskTitle(draft)
    val submit = {
        if (ready && valid != null && add(valid)) submittedAt = state.savedRevision
    }
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
    val dismiss = {
        if (!state.saving) {
            keyboard?.hide()
            close()
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add item") },
        text = {
            OutlinedTextField(
                draft, { draft = it.take(MAX_TASK_TITLE_LENGTH) }, Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true, enabled = !state.saving, label = { Text("Item name") },
                supportingText = state.issue?.let { issue -> { Text(issue) } },
                isError = state.issue != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !state.saving) { Text("Cancel") } },
        confirmButton = { Button(onClick = submit, enabled = ready && valid != null) { Text("Add") } },
    )
}

internal fun LazyListScope.taskRows(
    tasks: AgendaContent.Tasks, dates: AgendaDates, canComplete: Boolean, ready: Boolean,
    showCompleted: Boolean, toggleCompleted: () -> Unit, complete: (String, Boolean) -> Unit,
) {
    if (tasks.open.isEmpty()) item(key = "empty-tasks") {
        EmptyState("All done", null)
    }
    taskGroup(tasks.open, dates, canComplete, ready, complete)
    if (tasks.completed.isNotEmpty()) {
        item(key = "completed-header") {
            Surface(onClick = toggleCompleted, shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics {
                stateDescription = if (showCompleted) "Expanded" else "Collapsed"
            }.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Completed · ${tasks.completed.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (showCompleted) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                        Modifier.size(compactTileIconSize))
                }
            }
        }
        if (showCompleted) taskGroup(tasks.completed, dates, canComplete, ready, complete)
    }
}

private fun LazyListScope.taskGroup(
    tasks: List<AgendaTask>, dates: AgendaDates, writable: Boolean, ready: Boolean, complete: (String, Boolean) -> Unit,
) {
    itemsIndexed(tasks, key = { _, task -> "task:${task.uid}" }, contentType = { _, _ -> "task" }) { index, task ->
        TaskRow(task, dates, writable, ready, first = index == 0, last = index == tasks.lastIndex) { complete(task.uid, it) }
    }
}

@Composable
private fun TaskRow(
    task: AgendaTask, dates: AgendaDates, writable: Boolean, ready: Boolean, first: Boolean, last: Boolean,
    change: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = if (writable) Modifier.toggleable(task.completed, enabled = ready, role = Role.Checkbox, onValueChange = change)
    else Modifier.semantics(mergeDescendants = true) { stateDescription = if (task.completed) "Completed" else "Open" }
    val shape = when {
        first && last -> MaterialTheme.shapes.large
        first -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RectangleShape
    }
    Surface(shape = shape, color = colors.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).then(interaction).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Checkbox(checked = task.completed, onCheckedChange = null, enabled = writable && ready)
                Column(Modifier.weight(1f)) {
                    Text(task.summary, style = MaterialTheme.typography.titleMedium,
                        color = if (task.completed) colors.onSurfaceVariant else colors.onSurface)
                    if (!task.completed) task.due?.let { date -> AgendaSubtitle(dates.dueText(date)) }
                }
            }
            if (!last) HorizontalDivider(Modifier.padding(start = 54.dp, end = 16.dp), color = colors.outlineVariant)
        }
    }
}
