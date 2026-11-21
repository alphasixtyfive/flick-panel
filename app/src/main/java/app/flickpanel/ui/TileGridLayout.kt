package app.flickpanel.ui

import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

internal const val mediumWindowWidthDp = 600

internal fun dashboardTileColumns(windowWidth: Float, fontScale: Float): Int {
    val expanded = windowWidth >= tabletWidthDp
    val padding = if (expanded) panelWidePadding.value else 20f
    val contentWidth = minOf(windowWidth, panelMaxWidth.value) - padding * 2
    val minimumTileWidth = 170f * fontScale.coerceAtLeast(1f)

    return when {
        expanded && contentWidth >= minimumTileWidth * 4 + tileGap.value * 3 -> 4
        contentWidth >= minimumTileWidth * 2 + tileGap.value -> 2
        else -> 1
    }
}

internal fun tileSpan(tile: TileSpec, columns: Int, compact: Boolean, resizeTiles: Boolean): Int {
    val wider = resizeTiles && tile.kind.resizable && tile.wideOnTablet
    val needsTwoColumns = when (tile.kind) {
        TileKind.CAMERA -> true
        TileKind.SLIDER, TileKind.CLIMATE, TileKind.MEDIA -> wider || !resizeTiles && !compact
        else -> wider
    }
    return if (needsTwoColumns && columns > 1) 2 else 1
}

internal fun packTileRows(
    tiles: List<TileSpec>, columns: Int, compact: Boolean, resizeTiles: Boolean,
): List<List<TileSpec>> {
    val rows = mutableListOf<MutableList<TileSpec>>()
    var used = columns
    for (tile in tiles) {
        val span = tileSpan(tile, columns, compact, resizeTiles)
        if (used + span > columns) {
            rows.add(mutableListOf())
            used = 0
        }
        rows.last().add(tile)
        used += span
    }
    return rows
}
