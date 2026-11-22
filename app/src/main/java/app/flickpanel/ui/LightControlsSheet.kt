package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.LightColour
import app.flickpanel.ha.Link
import app.flickpanel.ha.canDim
import app.flickpanel.ha.colourTemperatures
import app.flickpanel.ha.lightColour
import app.flickpanel.ha.supportsColour
import kotlin.math.abs
import kotlin.math.roundToInt

private val lightColours = listOf(
    "Red" to LightColour(255, 64, 64),
    "Amber" to LightColour(255, 154, 43),
    "Green" to LightColour(66, 225, 113),
    "Cyan" to LightColour(59, 210, 231),
    "Blue" to LightColour(80, 121, 255),
    "Pink" to LightColour(255, 93, 161),
    "White" to LightColour(255, 255, 255),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LightControlsSheet(id: String, state: PanelUi, model: PanelViewModel) {
    val entity = state.entity(id)
    val live = state.entities[id]
    val pending = state.pending[id]
    val available = state.link == Link.ONLINE && live?.available == true
    val enabled = available && pending == null
    var preview by remember(id) { mutableStateOf<Int?>(null) }
    val level = preview ?: pending?.removeSuffix("%")?.toIntOrNull() ?: entity?.brightness ?: 0
    LaunchedEffect(enabled) { if (!enabled) preview = null }

    ModalBottomSheet(
        onDismissRequest = model::closeLight,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.entityName(id), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ViewerCloseButton("Close light controls", model::closeLight)
            }
            if (entity?.canDim == true) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Brightness", style = MaterialTheme.typography.titleMedium)
                        Text(if (level == 0) "Off" else "$level%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ControlSlider(
                        value = level / 100f,
                        label = "Brightness",
                        enabled = enabled,
                        change = { preview = (it * 100).roundToInt() },
                        finish = {
                            preview?.let { value -> live?.let { model.dim(it, value) } }
                            preview = null
                        },
                        stateLabel = if (level == 0) "Off" else "$level%",
                    )
                }
            }
            if (entity?.supportsColour == true) {
                LightColours(entity.lightColour, enabled) { model.setLightColour(id, it) }
            }
            entity?.colourTemperatures?.let { range ->
                val kelvin = entity.attributes.optInt("color_temp_kelvin")
                    .takeIf { entity.state == "on" && entity.attributes.optString("color_mode") == "color_temp" }
                WhiteTones(range, kelvin, enabled) { model.setLightTemperature(id, it) }
            }
            val status = if (!available) "Unavailable" else pending?.takeUnless { it.endsWith('%') }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ErrorText(state.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightColours(current: LightColour?, enabled: Boolean, select: (LightColour) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Colour", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, rgb) in lightColours) {
                val colour = Color(rgb.red, rgb.green, rgb.blue)
                val selected = current?.isCloseTo(rgb) == true
                Box(
                    Modifier.size(48.dp).clip(CircleShape).selectable(selected, enabled, role = Role.RadioButton) {
                        if (!selected) select(rgb)
                    }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(38.dp).background(colour.copy(alpha = if (enabled || selected) 1f else .45f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(21.dp),
                            tint = if (colour.luminance() > .45f) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhiteTones(range: IntRange, current: Int?, enabled: Boolean, select: (Int) -> Unit) {
    val tones = listOf(
        "Warm" to range.first,
        "Neutral" to range.first + (range.last - range.first) / 2,
        "Cool" to range.last,
    )
    Column {
        Text("White tone", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, kelvin) in tones) {
                val selected = current?.let { abs(it - kelvin) <= 25 } == true
                FilterChip(selected, { if (!selected) select(kelvin) }, label = { Text(name) },
                    modifier = Modifier.heightIn(min = 48.dp), enabled = enabled)
            }
        }
    }
}
