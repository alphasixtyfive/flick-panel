package app.flickpanel.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

internal val tileGap = 14.dp
internal val personTileHeight = 86.dp
internal val homeAgendaHeight = 172.dp
private val cellHeight = 132.dp
private val compactCellHeight = 110.dp
private val shortcutHeight = 64.dp

internal fun LazyListScope.tileRows(
    rows: List<List<TileSpec>>, state: PanelUi, model: PanelViewModel,
    columns: Int, compact: Boolean, resizeTiles: Boolean, keyPrefix: String = "tile:", shortcutRow: Boolean = false,
    homeAgenda: Boolean = false,
) {
    items(rows, key = { keyPrefix + it.first().entity }, contentType = { "tiles" }) { row ->
        TileRow(row, state, model, columns, compact, resizeTiles, shortcutRow = shortcutRow, homeAgenda = homeAgenda)
    }
}

@Composable
internal fun TileRow(
    row: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, compact: Boolean, resizeTiles: Boolean,
    shortcutRow: Boolean = false, homeAgenda: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Layout(modifier = modifier.fillMaxWidth(), content = {
        for (tile in row) key(tile.entity) {
            val minimum = when (tile.kind) {
                TileKind.APP -> if (compact) shortcutHeight else 76.dp
                TileKind.TREND -> if (shortcutRow) shortcutHeight else if (compact) compactCellHeight else cellHeight
                TileKind.CAMERA -> 142.dp
                TileKind.PERSON -> personTileHeight
                TileKind.IMAGE -> if (compact && tile.wideOnTablet) 190.dp else 144.dp
                TileKind.TASKS, TileKind.CALENDAR -> when {
                    homeAgenda -> homeAgendaHeight
                    compact -> 126.dp
                    else -> 144.dp
                }
                else -> if (compact) compactCellHeight else cellHeight
            }
            val modifier = Modifier.heightIn(min = minimum)
            PanelTile(tile, state, model, modifier, compactHistory = shortcutRow && tile.kind == TileKind.TREND,
                expandedAgenda = homeAgenda)
        }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val gap = tileGap.roundToPx().coerceAtMost(width / columns)
        val cells = width - gap * (columns - 1)
        var column = 0
        val slots = row.map { tile ->
            val start = cells * column / columns + gap * column
            column += tileSpan(tile, columns, compact, resizeTiles)
            val end = cells * column / columns + gap * (column - 1)
            start to (end - start)
        }
        val height = if (constraints.hasFixedHeight) constraints.maxHeight else
            constraints.constrainHeight(children.indices.maxOf { children[it].minIntrinsicHeight(slots[it].second) })
        val measured = children.mapIndexed { index, child -> child.measure(Constraints.fixed(slots[index].second, height)) }
        layout(width, height) {
            measured.forEachIndexed { index, child -> child.placeRelative(slots[index].first, 0) }
        }
    }
}
