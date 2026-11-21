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
    val brightnessPercent: Int? = null,
    val keepAwakeOverride: Boolean? = null,
    val camera: String? = null,
    val cameraOpenedAt: Long = 0,
    val musicOpen: String? = null,
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
    val keepsDisplayAwake get() = keepAwakeOverride ?: config.wallMode
    val hasViewer get() = camera != null || musicOpen != null || agendaOpen != null || contentOpen != null
    val canWakeFromIdle get() = configured && (clock || screenBlank) && !editor && !unlocking && !hasViewer

    fun entity(id: String) = entities[id] ?: catalogue.entity(id)
    fun label(spec: TileSpec) = spec.label ?: entity(spec.entity)?.name ?: spec.entity.substringAfter('.').replace('_', ' ')

    fun wakeDisplay(): PanelUi =
        if (canWakeFromIdle) copy(clock = false, screenBlank = false, page = Page.FAVOURITES) else this

    fun approachWake(sensorId: String): PanelUi {
        if (link != Link.ONLINE || config.presenceEntity != sensorId || entities[sensorId]?.state != "on") return this
        return wakeDisplay()
    }

    fun showCamera(id: String, openedAt: Long): PanelUi {
        if (camera == id || config.favourites.none { it.entity == id && it.kind == TileKind.CAMERA }) return this
        return closeViewer().copy(camera = id, cameraOpenedAt = openedAt, screenBlank = false, clock = false)
    }

    private fun closeViewer(): PanelUi = if (hasViewer) {
        copy(camera = null, cameraOpenedAt = 0L, musicOpen = null, agendaOpen = null, contentOpen = null)
    } else this

    private fun openTile(id: String, openedAt: Long): PanelUi {
        val kind = config.favourites.firstOrNull { it.entity == id }?.kind ?: return this
        return when {
            kind == TileKind.CAMERA -> showCamera(id, openedAt)
            kind == TileKind.MEDIA -> closeViewer().copy(clock = false, screenBlank = false, musicOpen = id)
            kind.isAgenda -> closeViewer().copy(clock = false, screenBlank = false, agendaOpen = id)
            kind.hasContentScreen -> closeViewer().copy(clock = false, screenBlank = false, contentOpen = id)
            else -> this
        }
    }

    fun remoteCommand(command: PanelCommand, openedAt: Long): PanelUi {
        if (!configured || link != Link.ONLINE || remoteName == null || editor || unlocking) return this
        return when (command) {
            PanelCommand.Wake -> wakeDisplay()
            PanelCommand.Sleep -> if (screenBlank) this else closeViewer().copy(screenBlank = true, clock = false)
            PanelCommand.ShowClock -> if (clock) this else closeViewer().copy(screenBlank = false, clock = true)
            PanelCommand.CloseViewer -> closeViewer()
            is PanelCommand.ShowPage -> closeViewer().copy(page = command.page, screenBlank = false, clock = false)
            is PanelCommand.SetBrightness -> copy(brightnessPercent = command.percent)
            is PanelCommand.KeepAwake -> copy(keepAwakeOverride = command.enabled)
            is PanelCommand.OpenCamera -> showCamera(command.entityId, openedAt)
            is PanelCommand.OpenTile -> openTile(command.entityId, openedAt)
        }
    }

    val message: String? get() = if (link != Link.ONLINE) null else config.messageEntity?.let(entities::get)
        ?.takeIf { it.available && it.state !in setOf("on", "off", "none", "No reminders") }
        ?.state?.replace(Regex("\\s+"), " ")?.trim()?.take(120)?.takeIf(String::isNotEmpty)

    fun activeHomeBadges(): List<ActiveHomeBadge> {
        if (link != Link.ONLINE) return emptyList()
        return config.homeBadges.mapNotNull { badge ->
            val entity = entities[badge.entity]?.takeIf { it.available && it.state.trim().lowercase() in badge.activeStates }
                ?: return@mapNotNull null
            ActiveHomeBadge(badge.label ?: entity.name, entity)
        }
    }
}

internal data class ActiveHomeBadge(val label: String, val entity: Entity) {
    fun details(): List<Pair<String, String?>> {
        val items = entity.attributes.optJSONArray("items") ?: return emptyList()
        return (0 until minOf(items.length(), 48)).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val name = (item.opt("name") as? String)?.trim()?.take(120)?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            name to (item.opt("value") as? String)?.trim()?.take(300)?.takeIf(String::isNotEmpty)
        }
    }
}
