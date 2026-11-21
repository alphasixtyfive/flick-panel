package app.flickpanel.ui

import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

internal const val mediumWindowWidthDp = 600
private const val minimumTileWidthDp = 170f

internal fun dashboardTileColumns(windowWidth: Float, fontScale: Float): Int {
    val expanded = windowWidth >= tabletWidthDp
    val padding = if (expanded) panelWidePadding.value else 20f
    val contentWidth = minOf(windowWidth, panelMaxWidth.value) - padding * 2
    val minimumTileWidth = minimumTileWidthDp * fontScale.coerceAtLeast(1f)

    return when {
        expanded && contentWidth >= minimumTileWidth * 4 + tileGap.value * 3 -> 4
        contentWidth >= minimumTileWidth * 2 + tileGap.value -> 2
        else -> 1
    }
}

internal fun roomTileColumns(availableWidth: Float, fontScale: Float): Int {
    val gap = tileGap.value
    val scale = fontScale.coerceAtLeast(1f)
    if (availableWidth < 2 * minimumTileWidthDp * scale + gap) return 1
    return ((availableWidth + gap) / (190f * scale + gap)).toInt().coerceIn(2, 5)
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

internal enum class HomeGroup(val label: String) {
    CONTROLS("Controls"), CAMERAS("Cameras"), PEOPLE("People"),
    AGENDA("Agenda"), APPS("Apps"), HISTORY("History"),
}

internal val TileKind.homeGroup: HomeGroup
    get() = when {
        this == TileKind.CAMERA -> HomeGroup.CAMERAS
        isAgenda -> HomeGroup.AGENDA
        this == TileKind.PERSON -> HomeGroup.PEOPLE
        this == TileKind.APP -> HomeGroup.APPS
        this == TileKind.TREND -> HomeGroup.HISTORY
        else -> HomeGroup.CONTROLS
    }

internal data class HomeTileGroups(
    val columns: Int,
    val compact: Boolean,
    val resizeTiles: Boolean,
    val controls: List<List<TileSpec>>,
    val cameras: List<List<TileSpec>>,
    val agenda: List<List<TileSpec>>,
    val agendaColumns: Int,
    val people: List<List<TileSpec>>,
    val peopleColumns: Int,
    val shortcuts: List<TileSpec>,
    val history: List<List<TileSpec>>,
)

internal fun homeTileGroups(tiles: List<TileSpec>, columns: Int, compact: Boolean, resizeTiles: Boolean): HomeTileGroups {
    val groups = tiles.groupBy { it.kind.homeGroup }
    val cameras = groups[HomeGroup.CAMERAS].orEmpty()
    val agenda = groups[HomeGroup.AGENDA].orEmpty()
    val agendaSlots = agenda.sumOf { tileSpan(it, columns, compact, resizeTiles) }
    val agendaColumns = agendaSlots.coerceIn(minOf(columns, 2), columns)
    val apps = groups[HomeGroup.APPS].orEmpty()
    val history = groups[HomeGroup.HISTORY].orEmpty()
    val peopleColumns = minOf(columns, 2)
    val compactGraph = if (compact && apps.size % columns == columns - 1) {
        history.firstOrNull { !it.wideOnTablet }
    } else null
    return HomeTileGroups(
        columns = columns,
        compact = compact,
        resizeTiles = resizeTiles,
        controls = packTileRows(groups[HomeGroup.CONTROLS].orEmpty(), columns, compact, resizeTiles),
        cameras = cameras.chunked(if (columns > 1) 2 else 1),
        agenda = packTileRows(agenda, agendaColumns, compact, resizeTiles),
        agendaColumns = agendaColumns,
        people = packTileRows(groups[HomeGroup.PEOPLE].orEmpty(), peopleColumns, compact, resizeTiles),
        peopleColumns = peopleColumns,
        shortcuts = apps + listOfNotNull(compactGraph),
        history = packTileRows(history.filterNot { it == compactGraph }, columns, compact, resizeTiles),
    )
}
