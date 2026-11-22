package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link
import app.flickpanel.ha.binaryStatus
import java.text.NumberFormat

@Composable
internal fun ValueTile(label: String, entity: Entity?, link: Link, modifier: Modifier) {
    val value = remember(entity) { displayValue(entity) }
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TileTitle(label)
            if (entity?.available == true) {
                val numeric = entity.state.toDoubleOrNull()?.isFinite() == true
                TileValue(value, prominent = numeric, maxLines = 2)
                if (link == Link.OFFLINE) TileStatus("Last reported")
            } else TileStatus(value)
        }
    }
}

internal fun displayValue(entity: Entity?, includeUnit: Boolean = true): String {
    if (entity == null || !entity.available) return "Unavailable"
    entity.binaryStatus()?.let { return it }
    val value = entity.state.take(120)
    return if (entity.unit.isBlank()) value.replaceFirstChar { it.uppercase() }
    else {
        val precision = when (entity.unit) {
            "°C", "°F" -> 1
            "W", "VA" -> 0
            else -> 3
        }
        val formatted = value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { formatNumber(it, precision) } ?: value
        if (includeUnit) "$formatted ${entity.unit}" else formatted
    }
}

internal fun formatNumber(value: Double, precision: Int): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = precision
        isGroupingUsed = false
    }.format(value)
