package app.flickpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
    reduceMotion: Boolean, modifier: Modifier, activate: () -> Unit,
) {
    val ready = available && pending == null
    val colors = MaterialTheme.colorScheme
    val action = tile.kind == TileKind.ACTION
    val confirming = remember(tile.entity) { mutableStateOf(false) }
    val running = timer?.state == "active" || timer?.state == "paused"
    val countdown = if (running) timerReadout(timer) else null
    val active = available && (if (action) running else on)
    val background by animateColorAsState(
        if (active) colors.primaryContainer else colors.surfaceContainer,
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
        contentColor = if (active) colors.onPrimaryContainer else colors.onSurface) {
        Column(Modifier.fillMaxSize().tileFill(countdown?.progress ?: 0f, colors.primary.copy(alpha = .16f))
            .padding(tileContentPadding), verticalArrangement = Arrangement.SpaceBetween) {
            ControlTileTitle(
                label,
                when {
                    action -> Icons.Rounded.PlayArrow
                    tile.entity.startsWith("light.") -> Icons.Rounded.Lightbulb
                    else -> Icons.Rounded.PowerSettingsNew
                },
                if (active) colors.onPrimaryContainer else colors.primary,
            )
            if (text != null) {
                if (ready) TileValue(text, maxLines = 2)
                else TileStatus(text, color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
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
