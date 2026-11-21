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

internal data class RoomSection(val area: Area, val tiles: List<TileSpec>)

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
                if (tile.showOnHome && (tile.entity.startsWith("light.") || tile.kind == TileKind.CLIMATE)) {
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
            else -> false
        }
        val discovered = catalogue.room(area.id).asSequence().map(Entry::entity)
            .filterNot { it.id in savedIds }
            .sortedWith(compareBy({ !shownByDefault(it) }, { it.domain != "light" }, { it.id !in favourites }))
            .take((roomTileLimit - savedCount).coerceAtLeast(0))
        return buildList {
            for (preference in saved.orEmpty()) {
                val device = catalogue.roomEntity(preference.entity)
                val tile = device?.let(::roomTile)
                add(roomControl(preference, device, tile))
            }
            for (device in discovered) {
                val tile = roomTile(device)
                val preference = RoomTilePreference(
                    device.id, lightFormat = tile.kind.takeIf { device.domain == "light" },
                    hidden = !shownByDefault(device),
                )
                add(roomControl(preference, device, tile))
            }
        }
    }

    private fun roomControl(preference: RoomTilePreference, device: Entity?, tile: TileSpec?): RoomControl {
        if (device == null || tile == null || !config.showRoomHeating && device.domain == "climate") {
            return RoomControl(preference, null)
        }
        val light = device.domain == "light"
        val canDim = light && TileKind.SLIDER in device.kinds()
        val kind = when (preference.lightFormat) {
            TileKind.SLIDER -> if (canDim) TileKind.SLIDER else TileKind.TOGGLE
            TileKind.TOGGLE -> if (light) TileKind.TOGGLE else tile.kind
            else -> tile.kind
        }
        return RoomControl(preference, tile.copy(kind = kind, wideOnTablet = preference.wideOnTablet), canDim)
    }

    private fun roomTile(device: Entity): TileSpec {
        val kind = if (favourites[device.id]?.kind == TileKind.SLIDER && TileKind.SLIDER in device.kinds()) {
            TileKind.SLIDER
        } else {
            checkNotNull(roomTileKind(device.id))
        }
        return TileSpec(device.id, kind)
    }
}
