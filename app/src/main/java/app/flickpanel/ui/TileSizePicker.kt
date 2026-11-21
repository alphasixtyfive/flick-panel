package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun TileSizePicker(wide: Boolean, change: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tile size", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileSizeChoice("Standard", columns = 1, selected = !wide, modifier = Modifier.weight(1f)) { change(false) }
            TileSizeChoice("Wide", columns = 2, selected = wide, modifier = Modifier.weight(1f)) { change(true) }
        }
        Text(
            "Wide spans up to two columns on larger windows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TileSizeChoice(
    label: String, columns: Int, selected: Boolean, modifier: Modifier, select: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = select,
        modifier = modifier.heightIn(min = 64.dp).semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val markerShape = RoundedCornerShape(3.dp)
                Box(Modifier.weight(columns.toFloat()).height(12.dp).background(
                    if (selected) colors.onPrimaryContainer else colors.primary, markerShape,
                ))
                repeat(4 - columns) {
                    Box(Modifier.weight(1f).height(12.dp).background(colors.outlineVariant, markerShape))
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
