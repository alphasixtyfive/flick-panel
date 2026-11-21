package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import app.flickpanel.ha.readContent

internal val panelCardShape = RoundedCornerShape(26.dp)

@Composable
internal fun PanelTile(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier, compact: Boolean,
    wideGraph: Boolean = false,
) {
    val label = state.label(tile)
    val entity = state.entity(tile.entity)
    val live = state.entities[tile.entity]
    val pending = state.pending[tile.entity]
    val online = state.link == Link.ONLINE
    val ready = online && live?.available == true && pending == null
    when (tile.kind) {
        TileKind.TOGGLE, TileKind.ACTION ->
            ToggleActionTile(tile, state, model, modifier, live, ready)
        TileKind.SLIDER -> BrightnessTile(label, entity, live, pending, ready, model, modifier)
        TileKind.CLIMATE -> ClimateTile(tile, state, model, modifier, live, ready, compact)
        TileKind.MEDIA -> MusicTile(label, entity, pending, ready, model, modifier)
        TileKind.CHOICE -> ChoiceTile(label, entity, pending, ready, modifier) { option ->
            live?.let { model.selectOption(it, option) }
        }
        TileKind.NUMBER -> NumberTile(label, entity, pending, ready, modifier) { increase ->
            live?.let { model.stepNumber(it, increase) }
        }
        TileKind.COVER -> CoverTile(tile.entity, label, entity, live, online, pending, modifier) { action, confirmed ->
            model.controlCover(tile.entity, action, confirmed)
        }
        TileKind.TASKS, TileKind.CALENDAR -> AgendaTile(tile, state, model, modifier)
        TileKind.PERSON -> PersonTile(label, live, online, modifier) {
            model.openContent(tile.entity)
        }
        TileKind.READER -> ReaderTile(label, readContent(entity, tile.readerAttribute), modifier) {
            model.openContent(tile.entity)
        }
        TileKind.IMAGE -> ImageTile(tile.entity, label, entity?.state, online && entity?.available == true, model, modifier)
        TileKind.WEATHER -> WeatherTile(label, entity, modifier)
        TileKind.TIMER -> TimerTile(label, entity, modifier)
        TileKind.VALUE, TileKind.TREND -> ValueTile(tile, state, model, modifier, wideGraph)
        TileKind.APP -> AppTile(tile, modifier)
        TileKind.CAMERA -> CameraTile(tile, state, model, modifier)
    }
}

@Composable
internal fun ControlTileTitle(label: String, icon: ImageVector, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(icon, null, tint = tint, modifier = Modifier.size(29.dp))
    }
}
