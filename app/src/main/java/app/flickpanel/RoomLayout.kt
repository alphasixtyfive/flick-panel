package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.RoomTilePreference
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.roomTileLimit
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity

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

    val sections: List<RoomSection> by lazy {
        val available = catalogue.rooms
        val rooms = config.roomIds?.let { ids ->
            val byId = available.associateBy(Area::id)
            ids.mapNotNull(byId::get)
        } ?: available
        rooms.mapNotNull { area ->
            val tiles = controls(area).mapNotNull { control ->
                control.tile.takeUnless { control.preference.hidden }
            }
            if (tiles.isEmpty() && area.id !in config.roomTiles) null else RoomSection(area, tiles)
        }
    }

    fun controls(area: Area): List<RoomControl> {
        val saved = config.roomTiles[area.id]
        val savedIds = saved.orEmpty().mapTo(hashSetOf(), RoomTilePreference::entity)
        val candidates = catalogue.room(area.id).map { it.entity }
            .sortedWith(compareBy({ it.domain != "light" }, { it.id !in favourites }))
        val existing = candidates.filter { it.id in savedIds }
        val discovered = candidates.filterNot { it.id in savedIds }.take((roomTileLimit - existing.size).coerceAtLeast(0))
        val devices = (existing + discovered).associateBy(Entity::id)
        val newControls = discovered.map { device ->
            val tile = roomTile(area, device, favourites[device.id])
            RoomTilePreference(device.id, lightFormat = tile.kind.takeIf { device.domain == "light" },
                hidden = saved != null)
        }
        return (saved.orEmpty() + newControls).map { preference ->
            val device = devices[preference.entity]
                ?.takeIf { config.showRoomHeating || it.domain != "climate" }
            if (device == null) return@map RoomControl(preference, null)
            val default = roomTile(area, device, favourites[device.id])
            val canDim = TileKind.SLIDER in device.kinds()
            val kind = when (preference.lightFormat) {
                TileKind.SLIDER -> if (canDim) TileKind.SLIDER else TileKind.TOGGLE
                TileKind.TOGGLE -> if (device.domain == "light") TileKind.TOGGLE else default.kind
                else -> default.kind
            }
            RoomControl(preference, default.copy(kind = kind, wideOnTablet = preference.wideOnTablet), canDim)
        }
    }

    private fun roomTile(area: Area, device: Entity, favourite: TileSpec?): TileSpec {
        val kind = when {
            device.domain == "climate" -> TileKind.CLIMATE
            favourite?.kind == TileKind.SLIDER && TileKind.SLIDER in device.kinds() -> TileKind.SLIDER
            else -> TileKind.TOGGLE
        }
        val name = device.name
        val prefix = "${area.name} "
        val label = favourite?.label ?: when {
            kind == TileKind.CLIMATE && name.equals(area.name, ignoreCase = true) -> "Heating"
            name.startsWith(prefix, ignoreCase = true) -> name.drop(prefix.length)
                .trimStart().replaceFirstChar { it.uppercase() }.ifBlank { name }
            else -> null
        }
        return TileSpec(device.id, kind, label)
    }
}
