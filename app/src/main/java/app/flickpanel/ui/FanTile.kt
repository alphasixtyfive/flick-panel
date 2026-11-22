package app.flickpanel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileAccent
import app.flickpanel.ha.Entity
import app.flickpanel.ha.canSetFanSpeed
import app.flickpanel.ha.fanPowerService
import app.flickpanel.ha.fanSpeed
import kotlin.math.roundToInt

@Composable
internal fun FanTile(
    label: String, entity: Entity?, live: Entity?, pending: String?, ready: Boolean,
    model: PanelViewModel, modifier: Modifier, accent: TileAccent?, icon: ImageVector? = null,
) {
    val turnOn = live?.state != "on"
    val step = live?.attributes?.optDouble("percentage_step")?.takeIf { it in 1.0..100.0 }?.roundToInt() ?: 5
    LevelTile(
        label, entity, entity?.fanSpeed, pending, ready, icon ?: Icons.Rounded.Air, modifier,
        toggle = live?.takeIf { it.fanPowerService(turnOn) != null }?.let { { model.fanPower(it.id, turnOn) } },
        setLevel = live?.takeIf { it.canSetFanSpeed }?.let { { level -> model.setFanSpeed(it.id, level) } },
        accent = accent,
        step = step,
    )
}
