package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.flickpanel.config.GraphColour

@Composable
internal fun graphAccent(colour: GraphColour?): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surfaceContainer.luminance() < .4f
    return when (colour) {
        null -> scheme.primary
        GraphColour.BLUE -> if (dark) Color(0xFF8AB8FF) else Color(0xFF1A5FAB)
        GraphColour.TEAL -> if (dark) Color(0xFF65DBD0) else Color(0xFF006B64)
        GraphColour.GREEN -> if (dark) Color(0xFFA3D578) else Color(0xFF466C16)
        GraphColour.AMBER -> if (dark) Color(0xFFFFC266) else Color(0xFF8B5603)
        GraphColour.ROSE -> if (dark) Color(0xFFFFA1AF) else Color(0xFF9B3654)
    }
}

private val graphColours = listOf(null) + GraphColour.entries

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraphColourPicker(selected: GraphColour?, change: (GraphColour?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Graph colour", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (colour in graphColours) {
                val accent = graphAccent(colour)
                FilterChip(
                    selected = selected == colour,
                    onClick = { change(colour) },
                    label = { Text(colour?.label ?: "Theme") },
                    leadingIcon = { Box(Modifier.size(12.dp).background(accent, CircleShape)) },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
