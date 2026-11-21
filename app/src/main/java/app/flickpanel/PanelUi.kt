package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.Link

internal enum class Page(val label: String) { FAVOURITES("Favourites"), ROOMS("Rooms"), CAMERAS("Cameras") }

internal data class RoomSection(val area: Area, val tiles: List<TileSpec>)

internal data class RoomLayout(
    val config: PanelConfig = PanelConfig(),
    val catalogue: Catalogue = Catalogue()
) {
    val sections: List<RoomSection> by lazy {
        val available = catalogue.rooms
        val rooms = config.roomIds?.let { ids ->
            val byId = available.associateBy(Area::id)
            ids.mapNotNull(byId::get)
        } ?: available
        val pinned = config.favourites.filter { it.showOnHome }.associateBy(TileSpec::entity)
        rooms.map { area ->
            val tiles = catalogue.room(area.id)
                .sortedWith(compareBy({ it.entity.domain != "light" }, { it.entity.id !in pinned }))
                .take(32)
                .map { entry -> roomTile(area, entry.entity, pinned[entry.entity.id]) }
            RoomSection(area, tiles)
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

internal data class PanelUi(
    val loading: Boolean = true,
    val configured: Boolean = false,
    val layout: RoomLayout = RoomLayout(),
    val link: Link = Link.OFFLINE,
    val serverAddress: String? = null,
    val entities: Map<String, Entity> = emptyMap(),
    val loadingCatalogue: Boolean = false,
    val page: Page = Page.FAVOURITES,
    val clock: Boolean = false,
    val camera: String? = null,
    val cameraOpenedAt: Long = 0,
    val musicBrowser: String? = null,
    val agendaOpen: String? = null,
    val unlocking: Boolean = false,
    val editor: Boolean = false,
    val connectionEditor: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val pending: Map<String, String> = emptyMap(),
    val graphs: Map<String, List<GraphPoint>> = emptyMap()
) {
    val config get() = layout.config
    val catalogue get() = layout.catalogue
    val roomSections get() = layout.sections

    fun entity(id: String) = entities[id] ?: catalogue.entity(id)
    fun label(spec: TileSpec) = spec.label ?: entity(spec.entity)?.name ?: spec.entity.substringAfter('.').replace('_', ' ')

    val message: String? get() = if (link != Link.ONLINE) null else config.messageEntity?.let(entities::get)
        ?.takeIf { it.available && it.state !in setOf("on", "off", "none", "No reminders") }
        ?.state?.replace(Regex("\\s+"), " ")?.trim()?.take(120)?.takeIf(String::isNotEmpty)
}
