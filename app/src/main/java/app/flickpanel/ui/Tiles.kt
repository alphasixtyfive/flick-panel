package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
internal fun TileGrid(
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
) {
    if (columns < 4) {
        TileGrid(tiles, state, model, columns = columns, resizeTiles = resizeTiles)
        return
    }

    val (apps, devices) = remember(tiles) { tiles.partition { it.kind == TileKind.APP } }
    val summary = if (apps.size >= 3) devices.firstOrNull { it.kind == TileKind.TREND }?.takeUnless { it.wideOnTablet } else null
    val controls = remember(devices, summary) { devices.filterNot { it == summary } }
    val camera = controls.firstOrNull { it.kind == TileKind.CAMERA }
    val rows = remember(controls, camera) {
        packTileRows(controls.filterNot { it.kind == TileKind.CAMERA }, columns = 2, compact = true, resizeTiles = true)
    }
    if (camera != null && rows.size >= 2) {
        val featureRows = rows.take(3)
        FeaturedCamera(camera, featureRows, state, model)
        val remaining = remember(controls, camera, featureRows) {
            val featured = featureRows.flatten().toSet() + camera
            controls.filterNot { it in featured }
        }
        TileGrid(remaining, state, model, columns = 4, compact = true)
    } else {
        TileGrid(controls, state, model, columns = 4, compact = true)
    }
    if (summary != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
            Column(Modifier.weight(1f)) {
                TileGrid(apps.take(4), state, model, columns = 2, compact = true)
            }
            PanelTile(summary, state, model, Modifier.weight(1f).height(shortcutHeight * 2 + tileGap),
                compact = true, wideGraph = true)
        }
        TileGrid(apps.drop(4), state, model, columns = 4, compact = true)
    } else {
        TileGrid(apps, state, model, columns = 4, compact = true)
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
            PanelTile(tile, state, model, modifier, compact)
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
