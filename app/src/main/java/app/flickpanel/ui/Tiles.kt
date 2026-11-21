package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
private val cellHeight = 132.dp
private val compactCellHeight = 110.dp
private val shortcutHeight = 64.dp

@Composable
private fun TileGrid(
    tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int,
    compact: Boolean = false, resizeTiles: Boolean = compact,
) {
    val rows = remember(tiles, columns, compact, resizeTiles) { packTileRows(tiles, columns, compact, resizeTiles) }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
        for (row in rows) TileRow(row, state, model, columns, compact, resizeTiles)
    }
}

@Composable
internal fun FavouritesTileGrid(
    tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, resizeTiles: Boolean,
    modifier: Modifier,
) {
    val compact = columns >= 4
    val resizable = compact || resizeTiles
    val (apps, devices) = remember(tiles, compact) {
        if (compact) tiles.partition { it.kind == TileKind.APP } else emptyList<TileSpec>() to tiles
    }
    val summary = if (apps.size >= 3) devices.firstOrNull { it.kind == TileKind.TREND }?.takeUnless { it.wideOnTablet } else null
    val controls = remember(devices, summary) { devices.filterNot { it == summary } }
    val camera = if (compact) controls.firstOrNull { it.kind == TileKind.CAMERA } else null
    val featureRows = remember(controls, camera) {
        if (camera == null) emptyList()
        else packTileRows(controls.filterNot { it.kind == TileKind.CAMERA }, columns = 2, compact = true, resizeTiles = true)
            .takeIf { it.size >= 2 }?.take(3).orEmpty()
    }
    val remaining = remember(controls, camera, featureRows) {
        if (featureRows.isEmpty()) controls else {
            val featured = featureRows.flatten().toSet() + camera
            controls.filterNot { it in featured }
        }
    }
    val controlRows = remember(remaining, columns, compact, resizable) {
        packTileRows(remaining, columns, compact, resizable)
    }
    val appRows = remember(apps, summary, columns, compact, resizable) {
        packTileRows(if (summary == null) apps else apps.drop(4), columns, compact, resizable)
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(tileGap),
    ) {
        if (camera != null && featureRows.isNotEmpty()) {
            item(key = "featured:${camera.entity}", contentType = "featured") {
                FeaturedCamera(camera, featureRows, state, model)
            }
        }
        tileRows(controlRows, state, model, columns, compact, resizable)
        if (summary != null) {
            item(key = "summary:${summary.entity}", contentType = "summary") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                    Column(Modifier.weight(1f)) {
                        TileGrid(apps.take(4), state, model, columns = 2, compact = true)
                    }
                    PanelTile(summary, state, model, Modifier.weight(1f).height(shortcutHeight * 2 + tileGap),
                        wideGraph = true)
                }
            }
        }
        tileRows(appRows, state, model, columns, compact, resizable)
    }
}

internal fun LazyListScope.tileRows(
    rows: List<List<TileSpec>>, state: PanelUi, model: PanelViewModel,
    columns: Int, compact: Boolean, resizeTiles: Boolean, keyPrefix: String = "tile:",
) {
    items(rows, key = { keyPrefix + it.first().entity }, contentType = { "tiles" }) { row ->
        TileRow(row, state, model, columns, compact, resizeTiles)
    }
}

@Composable
private fun FeaturedCamera(camera: TileSpec, rows: List<List<TileSpec>>, state: PanelUi, model: PanelViewModel) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
            for (row in rows) TileRow(row, state, model, columns = 2, compact = true, resizeTiles = true)
        }
        CameraTile(camera, state, model, Modifier.fillMaxSize())
    }) { children, constraints ->
        val gap = tileGap.roundToPx()
        val width = constraints.maxWidth
        val controlsWidth = (width - gap) / 2
        val grid = children[0].measure(Constraints.fixedWidth(controlsWidth))
        val preview = children[1].measure(Constraints.fixed(width - controlsWidth - gap, grid.height))
        layout(width, grid.height) {
            grid.placeRelative(0, 0)
            preview.placeRelative(controlsWidth + gap, 0)
        }
    }
}

@Composable
private fun TileRow(
    row: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, compact: Boolean, resizeTiles: Boolean,
) {
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        for (tile in row) key(tile.entity) {
            val minimum = when (tile.kind) {
                TileKind.APP -> if (compact) shortcutHeight else 76.dp
                TileKind.CAMERA -> 226.dp
                TileKind.IMAGE -> if (compact && tile.wideOnTablet) 190.dp else 144.dp
                TileKind.TASKS, TileKind.CALENDAR -> 154.dp
                else -> if (compact) compactCellHeight else cellHeight
            }
            val modifier = Modifier.heightIn(min = minimum)
            PanelTile(tile, state, model, modifier)
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
        val height = constraints.constrainHeight(children.indices.maxOf { children[it].minIntrinsicHeight(slots[it].second) })
        val measured = children.mapIndexed { index, child -> child.measure(Constraints.fixed(slots[index].second, height)) }
        layout(width, height) {
            measured.forEachIndexed { index, child -> child.placeRelative(slots[index].first, 0) }
        }
    }
}
