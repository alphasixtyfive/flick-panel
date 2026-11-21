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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Link
import app.flickpanel.ha.personPicture
import app.flickpanel.ha.readContent

internal val panelCardShape = RoundedCornerShape(26.dp)
internal val tileContentPadding = 16.dp
internal val tileIconSize = 32.dp
internal val compactTileIconSize = 28.dp
internal val tileArtworkSize = 36.dp

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
        TileKind.SLIDER -> BrightnessTile(label, entity, live, pending.takeIf { online }, ready, model, modifier)
        TileKind.CLIMATE -> ClimateTile(label, entity, live, pending, ready, modifier) { increase ->
            model.stepTemperature(tile.entity, increase)
        }
        TileKind.MEDIA -> MusicTile(label, entity, pending, ready, model, modifier)
        TileKind.CHOICE -> ChoiceTile(label, entity, pending, ready, modifier) { option ->
            live?.let { model.selectOption(it, option) }
        }
        TileKind.NUMBER -> NumberTile(label, entity, pending, ready, modifier) { increase ->
            live?.let { model.stepNumber(it, increase) }
        }
        TileKind.COVER -> CoverTile(label, entity, live, online, pending, modifier) { action ->
            model.controlCover(tile.entity, action)
        }
        TileKind.TASKS, TileKind.CALENDAR -> AgendaTile(tile, state, model, modifier)
        TileKind.PERSON -> {
            val photo = rememberPanelImage(live?.personPicture?.takeIf { online },
                model::cachedPersonAvatar, model::loadPersonAvatar)
            PersonTile(label, live, online, modifier, avatar = photo) { model.openContent(tile.entity) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TileTitle(label, Modifier.weight(1f).align(Alignment.Top))
        Icon(icon, null, tint = tint, modifier = Modifier.size(tileIconSize))
    }
}

@Composable
internal fun TileTitle(label: String, modifier: Modifier = Modifier, maxLines: Int = 2, color: Color = Color.Unspecified) {
    Text(label, modifier, style = MaterialTheme.typography.titleMedium, color = color,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun TileStatus(
    text: String, modifier: Modifier = Modifier, maxLines: Int = 2,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun TileValue(text: String, modifier: Modifier = Modifier, prominent: Boolean = false, maxLines: Int = 1) {
    val typography = MaterialTheme.typography
    val style = if (prominent) typography.headlineSmall else typography.titleLarge
    Text(text, modifier, style = style.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "'tnum'"),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
