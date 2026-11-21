package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TileSizePicker(wide: Boolean, change: (Boolean) -> Unit) {
    val labels = listOf("Standard", "Wide")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tile size", style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val equivalentWidth = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (equivalentWidth < 236f) {
                Column(Modifier.selectableGroup()) {
                    labels.forEachIndexed { index, label ->
                        val wideOption = index == 1
                        val selected = wide == wideOption
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium)
                                .selectable(selected, role = Role.RadioButton, onClick = { change(wideOption) })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow {
                    labels.forEachIndexed { index, label ->
                        val wideOption = index == 1
                        SegmentedButton(
                            selected = wide == wideOption,
                            onClick = { change(wideOption) },
                            shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                        ) { Text(label) }
                    }
                }
            }
        }
        Text(
            "Wide spans two columns when there’s room.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
