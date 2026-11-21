package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.CoverAction
import app.flickpanel.ha.Entity
import app.flickpanel.ha.canMoveCover
import app.flickpanel.ha.coverPosition
import app.flickpanel.ha.requiresCoverConfirmation
import app.flickpanel.ha.supportsCoverAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverTile(
    id: String, label: String, entity: Entity?, live: Entity?, online: Boolean, pending: String?,
    modifier: Modifier, move: (CoverAction, Boolean) -> Unit,
) {
    var confirming by remember(id) { mutableStateOf<CoverAction?>(null) }
    val canMove: (CoverAction) -> Boolean = { action -> online && live?.canMoveCover(action, pending) == true }
    val confirmation = confirming?.takeIf(canMove)
    LaunchedEffect(confirming, confirmation) {
        if (confirmation == null) confirming = null
    }

    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val status = if (online) pending ?: live?.coverStatus() ?: "Unavailable" else "Unavailable"
            Text(status,
                Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (action in CoverAction.entries) {
                    if (entity?.supportsCoverAction(action) != true) continue
                    FilledTonalIconButton(
                        enabled = canMove(action),
                        onClick = {
                            if (live?.requiresCoverConfirmation(action) == true) confirming = action
                            else move(action, false)
                        },
                    ) {
                        val icon = when (action) {
                            CoverAction.OPEN -> Icons.Rounded.KeyboardArrowUp
                            CoverAction.STOP -> Icons.Rounded.Stop
                            CoverAction.CLOSE -> Icons.Rounded.KeyboardArrowDown
                        }
                        Icon(icon, "${action.label} $label")
                    }
                }
            }
        }
    }

    if (confirmation != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("${confirmation.label} $label?") },
            text = { Text("Make sure the gate’s path is clear.") },
            confirmButton = {
                Button(onClick = { confirming = null; move(confirmation, true) }) { Text(confirmation.label) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

internal fun Entity.coverStatus(): String {
    if (state.isBlank() || state == "unavailable") return "Unavailable"
    val position = coverPosition()
    val label = when (state) {
        "open" -> if (position != null && position in 1..99) "Partly open" else "Open"
        "closed" -> "Closed"
        "opening" -> "Opening"
        "closing" -> "Closing"
        else -> "Unknown"
    }
    return if (position == null) label else "$label · $position%"
}
