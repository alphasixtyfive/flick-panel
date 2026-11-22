package app.flickpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity
import app.flickpanel.ha.vacuumBatteryPercent
import app.flickpanel.ha.vacuumStatus

@Composable
internal fun VacuumTile(
    label: String, entity: Entity?, batterySensor: Entity?, accent: TileAccent?, modifier: Modifier,
    compact: Boolean = false, icon: ImageVector? = null, open: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val surface = tileContainerColour(accent)
    val battery = vacuumBatteryPercent(entity, batterySensor)
    Surface(
        onClick = open, modifier = modifier.semantics { onClick("Open vacuum controls", null) },
        shape = panelCardShape, color = surface, contentColor = colors.onSurface,
    ) {
        ControlTileContent(label, compact, titleLines = 1, badge = {
            val tint = tileAccentColour(accent, surface)
            if (icon == null) TileIconBadge(surface, tint) { ink ->
                VacuumIcon(ink, Modifier.size(24.dp))
            } else TileIconBadge(icon, surface, tint)
        }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = Modifier.weight(1f, fill = false)
                if (compact) CompactControlStatus(vacuumStatus(entity), status)
                else TileValue(vacuumStatus(entity), status)
                battery?.let { VacuumBattery(it) }
            }
        }
    }
}

@Composable
private fun VacuumIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(color, radius * .84f, style = Stroke(radius * .17f))
        drawCircle(color, radius * .23f, center + Offset(0f, -radius * .28f))
        drawLine(color, center + Offset(-radius * .28f, radius * .37f),
            center + Offset(radius * .28f, radius * .37f), radius * .125f, cap = StrokeCap.Round)
    }
}
