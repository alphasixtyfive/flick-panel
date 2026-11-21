package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

internal val tileGap = 14.dp
private val cellHeight = 132.dp
private val compactCellHeight = 110.dp
private val shortcutHeight = 64.dp

@Composable
internal fun FavouritesTileGrid(
    tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, resizeTiles: Boolean,
    modifier: Modifier,
) {
    val compact = columns >= 4
    val resizable = compact || resizeTiles
    val groups = remember(tiles, columns, compact, resizable) {
        homeTileGroups(tiles, columns, compact, resizable)
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(tileGap),
    ) {
        tileRows(groups.controls, state, model, columns, compact, resizable)
        if (groups.overview.isNotEmpty()) {
            item(key = "home-overview", contentType = "home-overview") {
                TileRow(groups.overview, state, model, columns, compact, resizable, overview = true)
            }
        }
        items(groups.cameras, key = { "home-cameras:" + it.first().entity }, contentType = { "home-cameras" }) { cameras ->
            HomeCameraRow(cameras, columns, state, model)
        }
        tileRows(groups.agenda, state, model, columns, compact, resizable)
        if (groups.people.isNotEmpty()) {
            item(key = "people", contentType = "people") {
                PeopleRow(groups.people) { tile, cell -> PanelTile(tile, state, model, cell) }
            }
        }
        val shortcutColumns = minOf(columns, groups.shortcuts.size).coerceAtLeast(1)
        tileRows(groups.shortcuts.chunked(shortcutColumns), state, model, shortcutColumns, compact, resizable,
            shortcutRow = true)
        tileRows(groups.history, state, model, columns, compact, resizable)
    }
}

@Composable
private fun HomeCameraRow(cameras: List<TileSpec>, columns: Int, state: PanelUi, model: PanelViewModel) {
    val twoAcross = columns > 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
        for (camera in cameras) {
            CameraTile(camera, state, model, Modifier.weight(1f).height(if (twoAcross) 142.dp else 180.dp))
        }
        if (twoAcross && cameras.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun PeopleRow(people: List<TileSpec>, tile: @Composable (TileSpec, Modifier) -> Unit) {
    val minWidth = 162.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val canResize = maxWidth >= mediumWindowWidthDp.dp
        val columns = people.size + people.count { canResize && it.wideOnTablet }
        val visible = ((maxWidth + tileGap) / (minWidth + tileGap)).toInt().coerceIn(1, columns)
        val nextCardHint = if (visible < columns) 22.dp else 0.dp
        val cellWidth = (maxWidth - (tileGap * (visible - 1)) - nextCardHint) / visible
        LazyRow(horizontalArrangement = Arrangement.spacedBy(tileGap)) {
            items(people, key = TileSpec::entity) { person ->
                val width = if (canResize && person.wideOnTablet) minOf(maxWidth, cellWidth * 2 + tileGap) else cellWidth
                tile(person, Modifier.width(width).heightIn(min = 86.dp))
            }
        }
    }
}

internal fun LazyListScope.tileRows(
    rows: List<List<TileSpec>>, state: PanelUi, model: PanelViewModel,
    columns: Int, compact: Boolean, resizeTiles: Boolean, keyPrefix: String = "tile:", shortcutRow: Boolean = false,
) {
    items(rows, key = { keyPrefix + it.first().entity }, contentType = { "tiles" }) { row ->
        TileRow(row, state, model, columns, compact, resizeTiles, shortcutRow = shortcutRow)
    }
}

@Composable
private fun TileRow(
    row: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, compact: Boolean, resizeTiles: Boolean,
    overview: Boolean = false,
    shortcutRow: Boolean = false,
) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        for (tile in row) key(tile.entity) {
            val minimum = when (tile.kind) {
                TileKind.APP -> if (compact) shortcutHeight else 76.dp
                TileKind.TREND -> if (shortcutRow) shortcutHeight else if (compact) compactCellHeight else cellHeight
                TileKind.CAMERA -> 142.dp
                TileKind.IMAGE -> if (compact && tile.wideOnTablet) 190.dp else 144.dp
                TileKind.TASKS, TileKind.CALENDAR -> if (compact) 126.dp else 144.dp
                else -> if (compact) compactCellHeight else cellHeight
            }
            val modifier = Modifier.heightIn(min = minimum)
            PanelTile(tile, state, model, modifier, compactHistory = shortcutRow && tile.kind == TileKind.TREND)
        }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val gap = tileGap.roundToPx().coerceAtMost(width / columns)
        val cells = width - gap * (columns - 1)
        var column = 0
        val slots = row.map { tile ->
            val start = cells * column / columns + gap * column
            column += if (overview) 1 else tileSpan(tile, columns, compact, resizeTiles)
            val end = cells * column / columns + gap * (column - 1)
            start to (end - start)
        }
        val height = constraints.constrainHeight(children.indices.maxOf { children[it].minIntrinsicHeight(slots[it].second) })
        val measured = children.mapIndexed { index, child -> child.measure(Constraints.fixed(slots[index].second, height)) }
        layout(width, height) {
            measured.forEachIndexed { index, child -> child.placeRelative(slots[index].first, 0) }
        }
    }
}
