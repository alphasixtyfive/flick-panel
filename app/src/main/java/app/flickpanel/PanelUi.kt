package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelCommand
import app.flickpanel.ha.PanelRemoteState

internal enum class Page(val label: String) { FAVOURITES("Favourites"), ROOMS("Rooms"), CAMERAS("Cameras") }

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
    val screenBlank: Boolean = false,
    val camera: String? = null,
    val cameraOpenedAt: Long = 0,
    val musicBrowser: String? = null,
    val agendaOpen: String? = null,
    val contentOpen: String? = null,
    val unlocking: Boolean = false,
    val editor: Boolean = false,
    val connectionEditor: Boolean = false,
    val remoteName: String? = null,
    val remoteState: PanelRemoteState = PanelRemoteState.DISABLED,
    val busy: Boolean = false,
    val error: String? = null,
    val pending: Map<String, String> = emptyMap(),
    val graphs: Map<String, List<GraphPoint>> = emptyMap()
) {
    val config get() = layout.config
    val catalogue get() = layout.catalogue
    val roomSections get() = layout.sections
    val hasViewer get() = camera != null || musicBrowser != null || agendaOpen != null || contentOpen != null

    fun entity(id: String) = entities[id] ?: catalogue.entity(id)
    fun label(spec: TileSpec) = spec.label ?: entity(spec.entity)?.name ?: spec.entity.substringAfter('.').replace('_', ' ')

    fun showCamera(id: String, openedAt: Long): PanelUi {
        if (camera == id || config.favourites.none { it.entity == id && it.kind == TileKind.CAMERA }) return this
        return copy(camera = id, cameraOpenedAt = openedAt, screenBlank = false, clock = false, musicBrowser = null,
            agendaOpen = null, contentOpen = null)
    }

    fun remoteCommand(command: PanelCommand, openedAt: Long): PanelUi {
        if (!configured || link != Link.ONLINE || remoteName == null || editor || unlocking) return this
        return when (command) {
            PanelCommand.Wake -> if (screenBlank || clock) copy(screenBlank = false, clock = false, page = Page.FAVOURITES) else this
            PanelCommand.Sleep -> if (screenBlank) this else copy(
                screenBlank = true, clock = false, camera = null, musicBrowser = null, agendaOpen = null, contentOpen = null,
            )
            is PanelCommand.OpenCamera -> showCamera(command.entityId, openedAt)
        }
    }

    val message: String? get() = if (link != Link.ONLINE) null else config.messageEntity?.let(entities::get)
        ?.takeIf { it.available && it.state !in setOf("on", "off", "none", "No reminders") }
        ?.state?.replace(Regex("\\s+"), " ")?.trim()?.take(120)?.takeIf(String::isNotEmpty)
}
