package app.flickpanel.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity

@Composable
internal fun BrightnessTile(
    label: String,
    entity: Entity?,
    live: Entity?,
    pending: String?,
    enabled: Boolean,
    model: PanelViewModel,
    modifier: Modifier,
    accent: TileAccent?,
    icon: ImageVector? = null,
) = LevelTile(
    label, entity, entity?.brightness, pending, enabled, icon ?: Icons.Rounded.Lightbulb, modifier,
    toggle = live?.let { { model.toggle(it, entity?.state != "on") } },
    setLevel = live?.let { { level -> model.dim(it, level) } },
    adjust = entity?.let { { model.openLight(it.id) } },
    accent = accent,
    adjustIcon = icon,
)

@Composable
internal fun LevelTile(
    label: String, entity: Entity?, reportedLevel: Int?, pending: String?, enabled: Boolean,
    icon: ImageVector, modifier: Modifier, toggle: (() -> Unit)?, setLevel: ((Int) -> Unit)?,
    adjust: (() -> Unit)? = null, accent: TileAccent? = null, step: Int = 5, adjustIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val preview = remember(entity?.id) { mutableStateOf<Int?>(null) }
    val draggingLevel = preview.value
    val on = entity?.state == "on"
    val active = on && (enabled || pending != null)
    val surface = tileContainerColour(accent.takeIf { active })
    val fill = tileContainerColour(accent, active = true)
    val level = if (enabled || pending != null) {
        draggingLevel ?: pending?.removeSuffix("%")?.toIntOrNull() ?: reportedLevel
    } else null
    val filledLevel = level?.takeIf { active || draggingLevel != null || pending != null } ?: 0
    val status = when {
        draggingLevel != null -> "$draggingLevel%"
        pending != null -> pending
        !enabled -> "Unavailable"
        !on -> "Off"
        level != null -> "$level%"
        else -> "On"
    }
    val controls = when {
        toggle != null -> Modifier.toggleable(on, enabled = enabled, role = Role.Switch) { toggle() }
        setLevel != null -> Modifier.focusable(enabled = enabled)
        else -> Modifier
    }
    Surface(
        modifier = modifier.tileSlider(enabled && setLevel != null, level ?: 0, preview, step) { value ->
            setLevel?.invoke(value)
        }.then(controls).semantics { stateDescription = status },
        shape = panelCardShape,
        color = surface,
        contentColor = colors.onSurface,
    ) {
        Column(
            Modifier.fillMaxSize().tileFill(filledLevel / 100f, fill)
                .padding(horizontal = tileContentPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val tint = tileAccentColour(accent, surface, filledSurface = fill)
            ControlTileTitle(label, icon, surface, tint, adjust = adjust, enabled = enabled, maxLines = 1,
                adjustIcon = adjustIcon)
            if (status.endsWith('%') || enabled && pending == null) TileValue(status)
            else TileStatus(status)
        }
    }
}
