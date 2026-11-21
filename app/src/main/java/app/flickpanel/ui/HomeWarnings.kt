package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ActiveHomeBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeWarnings(badges: List<ActiveHomeBadge>) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .45f
    val container = if (dark) Color(0xff48391e) else Color(0xffffedc2)
    val content = if (dark) Color(0xffffdfa0) else Color(0xff533800)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (badge in badges) {
            AssistChip(
                onClick = { open = badge.entity.id },
                label = { Text(badge.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.WarningAmber, null, Modifier.size(20.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = content,
                    leadingIconContentColor = content),
                border = null,
            )
        }
    }
    val selected = badges.firstOrNull { it.entity.id == open }
    if (selected != null) {
        val close = { open = null }
        AlertDialog(onDismissRequest = close, title = { Text(selected.label) }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val items = selected.details()
                if (items.isEmpty()) {
                    val value = selected.entity.state
                    Text(if (value == "on") "Active" else value.replace('-', ' ').replaceFirstChar { it.uppercase() })
                }
                for ((name, detail) in items) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
    }
}
