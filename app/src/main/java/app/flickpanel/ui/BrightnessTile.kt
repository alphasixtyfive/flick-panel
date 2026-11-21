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
) {
    val colors = MaterialTheme.colorScheme
    val preview = remember(entity?.id) { mutableStateOf<Int?>(null) }
    val draggingLevel = preview.value
    val on = entity?.state == "on"
    val level = if (enabled || pending != null) {
        draggingLevel ?: pending?.removeSuffix("%")?.toIntOrNull() ?: entity?.brightness
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
        onClick = { live?.let { model.toggle(it, !on) } },
        enabled = enabled,
        modifier = modifier.tileSlider(enabled, level ?: 0, preview) { value ->
            live?.let { model.dim(it, value) }
        }.semantics { stateDescription = status },
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxSize().tileFill((level ?: 0) / 100f, colors.primaryContainer).padding(tileContentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ControlTileTitle(label, Icons.Rounded.Lightbulb, colors.primary)
            if (status.endsWith('%') || enabled && pending == null) TileValue(status)
            else TileStatus(status)
        }
    }
}
