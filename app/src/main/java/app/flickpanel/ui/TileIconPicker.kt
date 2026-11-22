package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileIcon

private val iconChoices = listOf<TileIcon?>(null) + TileIcon.entries.sortedBy(TileIcon::label)

@Composable
internal fun TileIconPicker(selected: TileIcon?, automatic: ImageVector, change: (TileIcon?) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    SettingsListGroup {
        SettingLink("Icon", selected?.label ?: "Automatic", { choosing = true }, selected?.symbol ?: automatic)
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("Tile icon") },
        text = {
            var search by remember { mutableStateOf("") }
            val query = search.trim()
            val choices = remember(query) {
                iconChoices.filter { option ->
                    if (option == null) "Automatic default".contains(query, ignoreCase = true)
                    else option.label.contains(query, ignoreCase = true) || option.keywords.contains(query, ignoreCase = true)
                }
            }
            val scroll = rememberLazyListState()
            LaunchedEffect(query) { if (choices.isNotEmpty()) scroll.scrollToItem(0) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupSearchField(search, { search = it }, "Search icons", Modifier.fillMaxWidth())
                if (choices.isEmpty()) {
                    Text("No matching icons")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp).selectableGroup(), state = scroll) {
                        items(choices, key = { it?.name ?: "automatic" }) { option ->
                            ListItem(
                                modifier = Modifier.selectable(option == selected, role = Role.RadioButton) {
                                    change(option)
                                    choosing = false
                                }.heightIn(min = 56.dp),
                                headlineContent = { Text(option?.label ?: "Automatic") },
                                leadingContent = { Icon(option?.symbol ?: automatic, null) },
                                trailingContent = { RadioButton(option == selected, onClick = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
    )
}
