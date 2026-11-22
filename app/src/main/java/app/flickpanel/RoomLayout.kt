package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.roomTileKind
import app.flickpanel.config.roomTileLimit
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Entry
import app.flickpanel.ha.canDim

internal data class RoomSection(val area: Area, val tiles: List<TileSpec>)

internal fun Area.tileName(name: String): String {
    if (!name.startsWith(this.name, ignoreCase = true)) return name
    val suffix = name.drop(this.name.length)
    if (suffix.firstOrNull()?.let { it.isWhitespace() || it == ':' } != true) return name
    return suffix.trimStart { it.isWhitespace() || it in ":-–—" }
        .replaceFirstChar(Char::uppercase).ifBlank { name }
}

internal data class RoomControl(
    val preference: RoomTilePreference,
    val tile: TileSpec?,
    val canDim: Boolean = false,
)

internal data class RoomLayout(
    val config: PanelConfig = PanelConfig(),
    val catalogue: Catalogue = Catalogue(),
) {
    private val favourites by lazy {
        config.favourites.filter { it.showOnHome }.associateBy(TileSpec::entity)
    }

    val selectedRooms: List<Area> by lazy {
        val available = catalogue.rooms.associateBy(Area::id)
        val saved = config.roomIds
        val ids = saved ?: buildList {
            for (tile in config.favourites) {
                if (tile.showOnHome && (tile.entity.startsWith("light.") || tile.kind == TileKind.CLIMATE || tile.kind == TileKind.FAN)) {
                    catalogue.areaOf(tile.entity)?.let(::add)
                }
            }
            addAll(config.roomTiles.keys)
        }
        ids.distinct().mapNotNull(available::get)
    }

    val sections: List<RoomSection> by lazy {
        selectedRooms.mapNotNull { area ->
            val tiles = controls(area).mapNotNull { control ->
                control.tile.takeUnless { control.preference.hidden }
            }
            if (tiles.isEmpty() && area.id !in config.roomTiles) null else RoomSection(area, tiles)
        }
    }

    fun controls(area: Area): List<RoomControl> {
        val saved = config.roomTiles[area.id]
        val savedIds = saved.orEmpty().mapTo(hashSetOf(), RoomTilePreference::entity)
        val savedCount = savedIds.count { catalogue.roomEntity(it) != null }
        fun shownByDefault(device: Entity) = when (device.domain) {
            "climate" -> true
            "light" -> saved == null
            "fan" -> saved == null && device.id in favourites
            else -> false
        }
        val discovered = catalogue.room(area.id).asSequence().map(Entry::entity)
            .filterNot { it.id in savedIds }
            .sortedWith(compareBy({ !shownByDefault(it) }, { it.domain != "light" }, { it.id !in favourites }))
            .take((roomTileLimit - savedCount).coerceAtLeast(0))
        return buildList {
            for (preference in saved.orEmpty()) {
                val device = catalogue.roomEntity(preference.entity)
                add(roomControl(preference, device))
            }
            for (device in discovered) {
                val preference = RoomTilePreference(
                    device.id,
                    hidden = !shownByDefault(device),
                )
                add(roomControl(preference, device))
            }
        }
    }

    private fun roomControl(preference: RoomTilePreference, device: Entity?): RoomControl {
        if (device == null || !config.showRoomHeating && device.domain == "climate") {
            return RoomControl(preference, null)
        }
        val canDim = device.canDim
        val default = checkNotNull(roomTileKind(device.id))
        val kind = if (canDim && preference.lightFormat != TileKind.TOGGLE) TileKind.SLIDER else default
        return RoomControl(preference, TileSpec(device.id, kind, wideOnTablet = preference.wideOnTablet), canDim)
    }
}
