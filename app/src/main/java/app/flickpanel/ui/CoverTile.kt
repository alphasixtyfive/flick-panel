package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.CoverAction
import app.flickpanel.ha.Entity
import app.flickpanel.ha.canMoveCover
import app.flickpanel.ha.coverPosition
import app.flickpanel.ha.gateToggleAction
import app.flickpanel.ha.supportsCoverAction

private val coverDirections = listOf(CoverAction.OPEN, CoverAction.CLOSE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverTile(
    label: String, entity: Entity?, live: Entity?, online: Boolean, pending: String?,
    modifier: Modifier, accent: TileAccent?, compact: Boolean, icon: ImageVector? = null, move: (CoverAction) -> Unit,
) {
    val status = if (online) pending ?: live?.coverStatus() ?: "Unavailable" else "Unavailable"
    if (entity?.isGate == true) {
        val action = if (online) live?.gateToggleAction(pending) else null
        GateToggleTile(label, status, online && live?.state == "open", action, modifier, accent, compact,
            icon ?: Icons.Rounded.Fence, move)
        return
    }
    val showStop = online && (pending == CoverAction.STOP.progress || live?.canMoveCover(CoverAction.STOP, pending) == true)
    val actions = if (showStop) listOf(CoverAction.STOP) else coverDirections

    Surface(modifier, shape = panelCardShape, color = tileContainerColour(accent),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier.fillMaxWidth().padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AccentedTileTitle(label, Icons.Rounded.Fence, accent, customIcon = icon)
            TileStatus(status)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (action in actions) {
                    if (entity?.supportsCoverAction(action) != true) continue
                    FilledTonalButton(
                        onClick = { move(action) },
                        enabled = online && live?.canMoveCover(action, pending) == true,
                        modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).semantics {
                            contentDescription = "${action.label} $label"
                        },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
                    ) { Text(action.label) }
                }
            }
        }
    }
}

@Composable
private fun GateToggleTile(
    label: String, status: String, isOpen: Boolean, action: CoverAction?, modifier: Modifier,
    accent: TileAccent?, compact: Boolean, icon: ImageVector, move: (CoverAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val usePrimary = isOpen && accent == null
    val surface = tileContainerColour(accent, isOpen)
    Surface(
        onClick = { action?.let(move) },
        enabled = action != null,
        modifier = modifier.semantics {
            role = Role.Button
            stateDescription = status
            if (action != null) onClick(label = "${action.label} $label", action = null)
        },
        shape = panelCardShape,
        color = surface,
        contentColor = if (usePrimary) colors.onPrimaryContainer else colors.onSurface,
    ) {
        ControlTileContent(label, compact, regularPadding = 16.dp, badge = {
            val tint = tileAccentColour(accent, surface, if (usePrimary) colors.onPrimaryContainer else colors.primary)
            TileIconBadge(icon, surface, tint)
        }) {
            val statusColor = if (usePrimary) colors.onPrimaryContainer else colors.onSurfaceVariant
            when {
                compact -> CompactControlStatus(status, color = statusColor)
                action != null -> TileValue(status, maxLines = 2)
                else -> TileStatus(status, color = statusColor)
            }
        }
    }
}

internal fun Entity.coverStatus(): String {
    val position = coverPosition()?.takeIf { it in 1..99 }
    return when (state) {
        "open" -> if (position == null) "Open" else "Partly open · $position%"
        "closed" -> "Closed"
        "opening" -> "Opening…"
        "closing" -> "Closing…"
        "unknown" -> "Unknown"
        else -> "Unavailable"
    }
}
