package app.flickpanel.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

internal val homePagerHeight = 56.dp

internal data class HomeTileRow(
    val tiles: List<TileSpec>,
    val columns: Int,
    val height: Dp,
    val expandedAgenda: Boolean = false,
    val shortcuts: Boolean = false,
)

internal data class HomeBand(val lanes: List<List<HomeTileRow>>) {
    val height = lanes.maxOf { rows -> rows.fold(tileGap * (rows.size - 1)) { sum, row -> sum + row.height } }
}

internal fun homeBands(
    groups: HomeTileGroups, fontScale: Float, availableHeight: Dp, gateIds: Set<String> = emptySet(),
): List<HomeBand> {
    val scale = fontScale.coerceAtLeast(1f)
    val columns = groups.columns
    val cameraHeight = (if (columns == 1) 180.dp else 164.dp) * scale
    val agendaHeight = homeAgendaHeight * scale
    val overviewCamera = if (availableHeight < 600.dp * scale) 160.dp * scale else 184.dp * scale
    val overviewHeight = overviewCamera + tileGap + agendaHeight
    val cameras = groups.cameras.firstOrNull()
    val agenda = groups.agenda.singleOrNull()?.takeIf { groups.agendaColumns == 2 }
    val sideBySide = columns == 4 && cameras?.size == 2 && agenda != null &&
        groups.people.isNotEmpty() && availableHeight >= overviewHeight + homePagerHeight
    val sidePeople = if (sideBySide) groups.people.take(3) else emptyList()
    val remainingPeople = groups.people.drop(sidePeople.size).flatten()
    val controls = groups.controls.flatten()
    val gates = controls.filter { it.entity in gateIds }
    val gatesBesideCameras = sideBySide && gates.isNotEmpty() &&
        gates.sumOf { tileSpan(it, 2, groups.compact, groups.resizeTiles) } <= 2
    val controlRows = if (gatesBesideCameras) {
        packTileRows(controls.filterNot { it.entity in gateIds }, columns, groups.compact, groups.resizeTiles)
    } else groups.controls

    fun HomeTileRow.band() = HomeBand(listOf(listOf(this)))
    val standardControlHeight = if (columns == 1) 100.dp else 110.dp
    fun controlHeight(tiles: List<TileSpec>) = tiles.maxOf { tile ->
        when (tile.kind) {
            TileKind.COVER -> 128.dp
            TileKind.CHOICE, TileKind.CLIMATE, TileKind.NUMBER -> 152.dp
            TileKind.IMAGE -> 164.dp
            TileKind.TOGGLE, TileKind.ACTION, TileKind.MEDIA -> if (availableHeight < 300.dp) 88.dp else standardControlHeight
            else -> standardControlHeight
        }
    } * scale

    return buildList {
        for ((index, row) in controlRows.withIndex()) {
            val height = if (index == 0 && gatesBesideCameras) maxOf(128.dp * scale, controlHeight(row)) else controlHeight(row)
            add(HomeTileRow(row, columns, height).band())
        }
        if (sideBySide) {
            val gateRow = if (gatesBesideCameras) HomeTileRow(gates, 2, 100.dp * scale) else null
            val peopleSpace = overviewHeight - (gateRow?.let { it.height + tileGap } ?: 0.dp)
            val personHeight = if (sidePeople.size == 3) (peopleSpace - tileGap * 2) / 3f else 86.dp * scale
            add(HomeBand(listOf(
                listOf(HomeTileRow(cameras, 4, overviewCamera), HomeTileRow(agenda, 2, agendaHeight, expandedAgenda = true)),
                listOfNotNull(gateRow) + sidePeople.map { row -> HomeTileRow(row, groups.peopleColumns, personHeight) },
            )))
        }
        for (row in groups.cameras.drop(if (sideBySide) 1 else 0)) {
            add(HomeTileRow(row, if (columns == 1) 1 else 4, cameraHeight).band())
        }
        if (!sideBySide) {
            for (row in groups.agenda) add(HomeTileRow(row, groups.agendaColumns, agendaHeight, expandedAgenda = true).band())
        }
        for (row in packTileRows(remainingPeople, columns, groups.compact, groups.resizeTiles)) {
            add(HomeTileRow(row, columns, personTileHeight * scale).band())
        }
        for (row in groups.shortcuts.chunked(columns)) add(HomeTileRow(row, columns, 68.dp * scale, shortcuts = true).band())
        for (row in groups.history) add(HomeTileRow(row, columns, 116.dp * scale).band())
    }
}

internal fun homePages(bands: List<HomeBand>, height: Dp): List<List<HomeBand>> {
    if (bands.isEmpty()) return emptyList()
    val fullHeight = bands.fold(tileGap * (bands.size - 1)) { sum, band -> sum + band.height }
    if (fullHeight <= height) return listOf(bands)

    val pageHeight = (height - homePagerHeight).coerceAtLeast(0.dp)
    val pages = mutableListOf<MutableList<HomeBand>>()
    var used = 0.dp
    for (band in bands) {
        val current = pages.lastOrNull()
        if (current == null || used + tileGap + band.height > pageHeight) {
            pages.add(mutableListOf(band))
            used = band.height
        } else {
            current.add(band)
            used += tileGap + band.height
        }
    }
    return pages
}
