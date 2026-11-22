package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphHistory
import app.flickpanel.ha.Link
import app.flickpanel.ha.canDim
import app.flickpanel.ha.personPicture
import app.flickpanel.ha.readContent

internal val panelCardShape = RoundedCornerShape(26.dp)
internal val tileContentPadding = 16.dp
internal val tileIconSize = 32.dp
internal val compactTileIconSize = 28.dp
internal val tileArtworkSize = 36.dp

@Composable
internal fun PanelTile(
    tile: TileSpec, state: PanelUi, model: PanelViewModel, modifier: Modifier,
    label: String, compactHistory: Boolean = false, expandedAgenda: Boolean = false, compactControls: Boolean = false,
) {
    if (tile.kind == TileKind.CAMERA) {
        CameraTile(tile, state, model, modifier)
        return
    }
    TileContent(
        tile = tile,
        label = label,
        entity = state.entity(tile.entity),
        live = state.entities[tile.entity],
        status = (tile.statusEntity ?: tile.vacuumBattery)?.let(state.entities::get),
        pending = state.pending[tile.entity],
        link = state.link,
        history = state.graphs[tile.entity],
        reduceMotion = state.config.reduceMotion,
        model = model,
        modifier = modifier,
        compactHistory = compactHistory,
        expandedAgenda = expandedAgenda,
        compactControls = compactControls,
    )
}

@Composable
private fun TileContent(
    tile: TileSpec, label: String, entity: Entity?, live: Entity?, status: Entity?, pending: String?,
    link: Link, history: GraphHistory?, reduceMotion: Boolean, model: PanelViewModel,
    modifier: Modifier, compactHistory: Boolean, expandedAgenda: Boolean, compactControls: Boolean,
) {
    val online = link == Link.ONLINE
    val available = online && live?.available == true
    val ready = available && pending == null
    val icon = tile.icon?.symbol
    when (tile.kind) {
        TileKind.TOGGLE, TileKind.ACTION -> {
            val on = entity?.state == "on"
            val adjust = if (entity?.canDim == true) ({ model.openLight(tile.entity) }) else null
            ToggleActionTile(tile, label, on, status, pending, available, reduceMotion, modifier, adjust, compactControls) {
                live?.let { if (tile.kind == TileKind.ACTION) model.action(it) else model.toggle(it, !on) }
            }
        }
        TileKind.SLIDER -> BrightnessTile(label, entity, live, pending.takeIf { online }, ready, model, modifier, tile.accent, icon)
        TileKind.FAN -> FanTile(label, entity, live, pending.takeIf { online }, ready, model, modifier, tile.accent, icon)
        TileKind.CLIMATE -> ClimateTile(label, entity, live, pending, ready, modifier, tile.accent, icon) { increase ->
            model.stepTemperature(tile.entity, increase)
        }
        TileKind.MEDIA -> MusicTile(label, entity, available, model, modifier, tile.accent, compactControls, icon)
        TileKind.CHOICE -> ChoiceTile(label, entity, pending, ready, modifier, tile.accent, icon) { option ->
            live?.let { model.selectOption(it, option) }
        }
        TileKind.NUMBER -> NumberTile(label, entity, pending, ready, modifier, tile.accent, icon) { increase ->
            live?.let { model.stepNumber(it, increase) }
        }
        TileKind.COVER -> CoverTile(label, entity, live, online, pending, modifier, tile.accent, compactControls, icon) { action ->
            model.controlCover(tile.entity, action)
        }
        TileKind.TASKS, TileKind.CALENDAR -> AgendaTile(tile, label, available, model, modifier, expandedAgenda)
        TileKind.PERSON -> {
            val photo = rememberPanelImage(live?.personPicture?.takeIf { online },
                model::cachedPersonAvatar, model::loadPersonAvatar)
            PersonTile(label, live, online, modifier, avatar = photo) { model.openContent(tile.entity) }
        }
        TileKind.READER -> ReaderTile(label, readContent(entity, tile.readerAttribute), modifier, tile.accent, icon) {
            model.openContent(tile.entity)
        }
        TileKind.VACUUM -> VacuumTile(
            label, live.takeIf { online }, status.takeIf { online },
            tile.accent, modifier, compact = compactControls, icon = icon,
        ) { model.openVacuum(tile.entity) }
        TileKind.IMAGE -> ImageTile(tile.entity, label, entity?.state, online && entity?.available == true, model, modifier)
        TileKind.WEATHER -> WeatherTile(label, entity, modifier)
        TileKind.TIMER -> TimerTile(label, entity, modifier, tile.accent, icon)
        TileKind.VALUE -> ValueTile(label, entity, link, modifier, tile.accent, icon)
        TileKind.PERCENTAGE -> PercentageTile(label, entity, link, modifier, tile.accent, icon)
        TileKind.TREND -> GraphTile(tile, label, entity, link, history, model::loadGraph, modifier, compactHistory)
        TileKind.APP -> AppTile(tile, modifier)
        TileKind.CAMERA -> Unit
    }
}

@Composable
internal fun ControlTileContent(
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    titleLines: Int = 2,
    regularPadding: Dp = 12.dp,
    headingDetail: @Composable () -> Unit = {},
    badge: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val content = modifier.fillMaxSize().padding(
        horizontal = tileContentPadding,
        vertical = if (compact) 10.dp else regularPadding,
    )
    val iconGap = Arrangement.spacedBy(8.dp)
    if (compact) {
        Row(content, horizontalArrangement = iconGap, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TileTitle(label, maxLines = 1)
                detail()
            }
            badge()
        }
    } else {
        Column(content, verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = iconGap, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).align(Alignment.Top)) {
                    TileTitle(label, maxLines = titleLines)
                    headingDetail()
                }
                badge()
            }
            detail()
        }
    }
}

@Composable
internal fun CompactControlStatus(
    text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, modifier, style = MaterialTheme.typography.bodyLarge, color = color,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun ControlTileTitle(
    label: String, icon: ImageVector, surface: Color, tint: Color, adjust: (() -> Unit)? = null, enabled: Boolean = true,
    maxLines: Int = 2, adjustIcon: ImageVector? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TileTitle(label, Modifier.weight(1f).align(Alignment.Top), maxLines = maxLines)
        ControlTileBadge(label, icon, surface, tint, adjust, enabled, adjustIcon)
    }
}

@Composable
internal fun ControlTileBadge(
    label: String, icon: ImageVector, surface: Color, tint: Color,
    adjust: (() -> Unit)?, enabled: Boolean, adjustIcon: ImageVector?,
) {
    if (adjust == null) TileIconBadge(icon, surface, tint)
    else IconButton(onClick = adjust, enabled = enabled) {
        TileIconBadge(adjustIcon ?: Icons.Rounded.Tune, surface, tint, description = "Adjust $label")
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
