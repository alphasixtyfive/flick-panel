package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.flickpanel.PanelViewModel
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
) = LevelTile(
    label, entity, entity?.brightness, pending, enabled, Icons.Rounded.Lightbulb, modifier,
    toggle = live?.let { { model.toggle(it, entity?.state != "on") } },
    setLevel = live?.let { { level -> model.dim(it, level) } },
    adjust = entity?.let { { model.openLight(it.id) } },
)

@Composable
internal fun LevelTile(
    label: String, entity: Entity?, reportedLevel: Int?, pending: String?, enabled: Boolean,
    icon: ImageVector, modifier: Modifier, toggle: (() -> Unit)?, setLevel: ((Int) -> Unit)?,
    adjust: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val preview = remember(entity?.id) { mutableStateOf<Int?>(null) }
    val draggingLevel = preview.value
    val on = entity?.state == "on"
    val level = if (enabled || pending != null) {
        draggingLevel ?: pending?.removeSuffix("%")?.toIntOrNull() ?: reportedLevel
    } else null
    val status = when {
        draggingLevel != null -> "$draggingLevel%"
        pending != null -> pending
        !enabled -> "Unavailable"
        !on -> "Off"
        level != null -> "$level%"
        else -> "On"
    }
    Surface(
        onClick = { toggle?.invoke() },
        enabled = enabled && toggle != null,
        modifier = modifier.tileSlider(enabled && setLevel != null, level ?: 0, preview) { value ->
            setLevel?.invoke(value)
        }.semantics { stateDescription = status },
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxSize().tileFill((level ?: 0) / 100f, colors.primaryContainer).padding(tileContentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ControlTileTitle(label, icon, colors.primary, adjust = adjust, enabled = enabled)
            if (status.endsWith('%') || enabled && pending == null) TileValue(status)
            else TileStatus(status)
        }
    }
}
