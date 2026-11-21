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
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link

@Composable
internal fun ToggleActionTile(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier,
    live: Entity?, ready: Boolean,
) {
    val label = state.label(tile)
    val pending = state.pending[tile.entity]
    val colors = MaterialTheme.colorScheme
    val on = state.entity(tile.entity)?.state == "on"
    val action = tile.kind == TileKind.ACTION
    val confirming = remember(tile.entity) { mutableStateOf(false) }
    val timer = tile.statusEntity?.let(state.entities::get)
    val running = timer?.state == "active" || timer?.state == "paused"
    val countdown = if (running) timerReadout(timer) else null
    val active = state.link == Link.ONLINE && live?.available == true && (if (action) running else on)
    val background by animateColorAsState(
        if (active) colors.primaryContainer else colors.surfaceContainer,
        animationSpec = tween(durationMillis = if (state.config.reduceMotion) 0 else 140),
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
        if (live != null) {
            when {
                action && tile.confirmAction -> confirming.value = true
                action -> model.action(live)
                else -> model.toggle(live, !on)
            }
        }
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
                    live?.domain == "light" || tile.entity.startsWith("light.") -> Icons.Rounded.Lightbulb
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
    if (confirming.value && live != null) {
        ConfirmTileAction(label, ready, dismiss = { confirming.value = false }) {
            confirming.value = false
            model.action(live)
        }
    }
}
