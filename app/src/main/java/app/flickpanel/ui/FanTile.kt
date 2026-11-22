package app.flickpanel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Entity
import app.flickpanel.ha.canSetFanSpeed
import app.flickpanel.ha.fanPowerService
import app.flickpanel.ha.fanSpeed

@Composable
internal fun FanTile(
    label: String, entity: Entity?, live: Entity?, pending: String?, ready: Boolean,
    model: PanelViewModel, modifier: Modifier,
) {
    val turnOn = live?.state != "on"
    LevelTile(
        label, entity, entity?.fanSpeed, pending, ready, Icons.Rounded.Air, modifier,
        toggle = live?.takeIf { it.fanPowerService(turnOn) != null }?.let { { model.fanPower(it.id, turnOn) } },
        setLevel = live?.takeIf { it.canSetFanSpeed }?.let { { level -> model.setFanSpeed(it.id, level) } },
    )
}
