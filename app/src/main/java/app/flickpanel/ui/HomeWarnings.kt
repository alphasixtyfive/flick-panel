package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ActiveHomeBadge
import app.flickpanel.config.TileAccent
import app.flickpanel.config.badgeStateLabel

@Composable
internal fun HomeWarnings(
    badges: List<ActiveHomeBadge>, modifier: Modifier = Modifier, prominent: Boolean = false, iconOnly: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainerLow
    val content = colors.onSurfaceVariant
    val warning = tileAccentColour(TileAccent.AMBER, container, content)
    val single = badges.singleOrNull()
    val label = single?.label ?: "${badges.size} alerts"
    val issueCount = single?.issueCount
    Surface(
        modifier = modifier,
        shape = CircleShape,
        onClick = { open = single?.entity?.id ?: "" },
        color = container,
        contentColor = content,
    ) {
        if (iconOnly) Box(Modifier.size(dashboardPillHeight), contentAlignment = Alignment.Center) {
            BadgedBox(badge = { if (issueCount != null) AlertCounter(issueCount) }) {
                Icon(Icons.Rounded.WarningAmber, label, Modifier.size(22.dp), tint = warning)
            }
        } else Row(
            Modifier.heightIn(min = dashboardPillHeight).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WarningAmber, null, Modifier.size(20.dp), tint = warning)
            Text(label, Modifier.weight(1f, fill = false),
                style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (issueCount != null) AlertCounter(issueCount)
        }
    }
    val selected = badges.firstOrNull { it.entity.id == open }
    if (open == "") {
        AlertDialog(onDismissRequest = { open = null }, title = { Text("Home alerts") }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                for (badge in badges) TextButton(onClick = { open = badge.entity.id }) { Text(badge.summary) }
            }
        }, confirmButton = { TextButton(onClick = { open = null }) { Text("Done") } })
    } else if (selected != null) {
        val close = { open = null }
        AlertDialog(onDismissRequest = close, title = { Text(selected.label) }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val items = selected.details()
                if (items.isEmpty()) {
                    val value = selected.entity.state
                    Text(if (value == "on") "Active" else badgeStateLabel(value))
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

@Composable
private fun AlertCounter(count: Int) {
    val colors = MaterialTheme.colorScheme
    Badge(
        modifier = Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp),
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Text(
            count.toString(),
            Modifier.clearAndSetSemantics { contentDescription = if (count == 1) "1 issue" else "$count issues" },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
