package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Area
import app.flickpanel.ha.Entity
import app.flickpanel.ha.VacuumAction
import app.flickpanel.ha.canRunVacuum
import app.flickpanel.ha.supportsVacuum
import app.flickpanel.ha.vacuumPrimaryAction
import app.flickpanel.ha.vacuumReady

@Composable
internal fun VacuumBattery(level: Int, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Battery $level%" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
            val stroke = 1.5.dp.toPx()
            val edge = stroke / 2
            val body = Size(size.width - 4.dp.toPx(), size.height - stroke)
            val terminal = Size(2.dp.toPx(), 4.dp.toPx())
            val innerRadius = CornerRadius(1.dp.toPx())
            drawRoundRect(tint, Offset(edge, edge), body, CornerRadius(2.dp.toPx()), style = Stroke(stroke))
            drawRoundRect(tint, Offset(body.width + stroke, center.y - terminal.height / 2), terminal, innerRadius)

            val inset = 2.25.dp.toPx()
            val chargeWidth = (body.width - inset * 2) * level.coerceIn(0, 100) / 100f
            if (chargeWidth > 0f) {
                drawRoundRect(tint, Offset(edge + inset, edge + inset),
                    Size(chargeWidth, body.height - inset * 2), innerRadius)
            }
        }
        TileStatus("$level%", maxLines = 1, color = tint)
    }
}

@Composable
internal fun VacuumRoomsPane(
    rooms: List<Area>, selected: List<String>, message: String?, retry: (() -> Unit)?, clear: (() -> Unit)?,
    enabled: Boolean, select: (String) -> Unit, modifier: Modifier, actions: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rooms", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            if (retry != null) {
                TextButton(onClick = retry, contentPadding = PaddingValues(0.dp)) { Text("Retry rooms") }
            } else if (clear != null) {
                TextButton(onClick = clear, contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.semantics { onClick(label = "Clear room selection", action = null) }) {
                    Text("Clear")
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (message != null) item(key = "message") {
                Text(message, Modifier.padding(vertical = 8.dp), color = colors.onSurfaceVariant)
            }
            itemsIndexed(rooms, key = { _, room -> room.id }) { index, room ->
                val checked = room.id in selected
                val top = if (index == 0) 20.dp else 4.dp
                val bottom = if (index == rooms.lastIndex) 20.dp else 4.dp
                val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                ListItem(
                    headlineContent = { Text(room.name) },
                    trailingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { select(room.id) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (checked) colors.secondaryContainer else colors.surfaceContainerLow,
                        headlineColor = if (checked) colors.onSecondaryContainer else colors.onSurface,
                    ),
                )
            }
        }
        actions()
    }
}

@Composable
internal fun VacuumActions(
    vacuum: Entity?, roomCount: Int, pending: Boolean, roomActionsReady: Boolean, run: (VacuumAction) -> Unit,
) {
    val primary = vacuum?.vacuumPrimaryAction(roomCount > 0)
    val label = when {
        pending -> "Sending…"
        primary == VacuumAction.START -> if (vacuum?.state == "paused") "Resume" else "Clean all rooms"
        primary == VacuumAction.PAUSE -> "Pause"
        primary == VacuumAction.CLEAN_AREA -> if (roomCount == 1) "Clean 1 room" else "Clean $roomCount rooms"
        vacuum?.vacuumReady == true && vacuum.supportsVacuum(VacuumAction.CLEAN_AREA) -> "Select rooms"
        else -> null
    }
    val stop = vacuum?.canRunVacuum(VacuumAction.STOP) == true
    val dock = vacuum?.canRunVacuum(VacuumAction.DOCK) == true
    if (label == null && !stop && !dock) return

    PanelActionFooter(gutter = 0.dp) {
        if (label != null) {
            Button(
                onClick = { primary?.let(run) },
                enabled = primary != null && !pending && (primary != VacuumAction.CLEAN_AREA || roomActionsReady),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(label) }
        }
        if (stop) {
            FilledTonalButton(onClick = { run(VacuumAction.STOP) }, enabled = !pending,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Stop") }
        }
        if (dock) {
            FilledTonalButton(onClick = { run(VacuumAction.DOCK) }, enabled = !pending,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Dock") }
        }
    }
}
