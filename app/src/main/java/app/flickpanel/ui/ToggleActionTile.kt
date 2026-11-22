package app.flickpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity

@Composable
internal fun ToggleActionTile(
    tile: TileSpec, label: String, on: Boolean, timer: Entity?, pending: String?, available: Boolean,
    reduceMotion: Boolean, modifier: Modifier, adjust: (() -> Unit)? = null, compact: Boolean, activate: () -> Unit,
) {
    val ready = available && pending == null
    val colors = MaterialTheme.colorScheme
    val action = tile.kind == TileKind.ACTION
    val timed = action && tile.statusEntity != null
    val domain = tile.entity.substringBefore('.')
    val icon = tile.icon?.symbol
    val confirming = remember(tile.entity) { mutableStateOf(false) }
    val countdown = if (timed && available) timerReadout(timer, idleLabel = "Ready", unavailableLabel = "Timer unavailable")
        else null
    val active = available && !action && on
    val usePrimary = active && tile.accent == null
    val tileSurface = tileContainerColour(tile.accent.takeIf { action || active }, active)
    val timerFill = if (timed && tile.accent != null) tileContainerColour(tile.accent, active = true) else null
    val iconTint = tileAccentColour(tile.accent, tileSurface, if (usePrimary) colors.onPrimaryContainer else colors.primary,
        filledSurface = timerFill ?: tileSurface)
    val background by animateColorAsState(
        tileSurface,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 140),
        label = "Tile state",
    )
    val text = pending ?: when {
        !ready -> "Unavailable"
        countdown != null -> countdown.label
        action -> null
        on -> "On"
        else -> "Off"
    }
    Surface(onClick = {
        if (action && tile.confirmAction) confirming.value = true else activate()
    }, enabled = ready, modifier = modifier.semantics {
        if (text != null) stateDescription = text
        if (action) {
            if (ready) onClick(label = "Run $label", action = null)
        } else {
            role = Role.Switch
            toggleableState = ToggleableState(on)
        }
    }, shape = panelCardShape, color = background,
        contentColor = if (usePrimary) colors.onPrimaryContainer else colors.onSurface) {
        val progress = Modifier.tileFill(countdown?.progress ?: 0f, timerFill ?: iconTint.copy(alpha = .18f))
        ControlTileContent(label, compact, progress, badge = {
            val symbol = icon ?: when {
                action -> actionTileIcon(domain, timed)
                domain == "light" -> Icons.Rounded.Lightbulb
                else -> Icons.Rounded.PowerSettingsNew
            }
            ControlTileBadge(label, symbol, tileSurface, iconTint, adjust, ready, icon)
        }) {
            if (text != null) {
                val emphasized = ready && (!timed || countdown?.running == true)
                val statusColor = when {
                    usePrimary -> colors.onPrimaryContainer
                    emphasized -> colors.onSurface
                    else -> colors.onSurfaceVariant
                }
                when {
                    compact -> CompactControlStatus(text, color = statusColor)
                    emphasized -> TileValue(text, maxLines = 2)
                    else -> TileStatus(text, color = statusColor)
                }
            }
        }
    }
    if (confirming.value) {
        ConfirmTileAction(label, ready, dismiss = { confirming.value = false }) {
            confirming.value = false
            activate()
        }
    }
}

internal fun actionTileIcon(domain: String, hasTimer: Boolean = false) = when {
    hasTimer -> Icons.Rounded.Timer
    domain == "scene" -> Icons.Rounded.AutoAwesome
    domain == "button" || domain == "input_button" -> Icons.Rounded.TouchApp
    else -> Icons.Rounded.PlayArrow
}
