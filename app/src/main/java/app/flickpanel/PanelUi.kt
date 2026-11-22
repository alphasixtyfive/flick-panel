package app.flickpanel

import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphHistory
import app.flickpanel.ha.Link
import app.flickpanel.ha.PanelCommand
import app.flickpanel.ha.PanelRemoteState

internal enum class Page(val label: String) { FAVOURITES("Favourites"), ROOMS("Rooms"), CAMERAS("Cameras") }

internal sealed interface PanelViewer {
    val id: String

    data class Camera(override val id: String, val openedAt: Long) : PanelViewer
    data class Music(override val id: String) : PanelViewer
    data class Agenda(override val id: String) : PanelViewer
    data class Content(override val id: String) : PanelViewer
    data class Light(override val id: String) : PanelViewer
}

private val whitespace = Regex("\\s+")
private val emptyMessages = setOf("on", "off", "none", "No reminders")

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
    val viewer: PanelViewer? = null,
    val unlocking: Boolean = false,
    val editor: Boolean = false,
    val connectionEditor: Boolean = false,
    val remoteName: String? = null,
    val remoteState: PanelRemoteState = PanelRemoteState.DISABLED,
    val busy: Boolean = false,
    val error: String? = null,
    val pending: Map<String, String> = emptyMap(),
    val graphs: Map<String, GraphHistory> = emptyMap()
) {
    val config get() = layout.config
    val catalogue get() = layout.catalogue
    val roomSections get() = layout.sections
    val keepsDisplayAwake get() = keepAwakeOverride ?: config.wallMode
    val displayBrightness: Int?
        get() {
            if (screenBlank) return 0
            val dashboard = brightnessPercent ?: config.dashboardBrightness
            if (!configured || !clock) return dashboard
            val screensaver = config.screensaverBrightness
                ?: config.defaultScreensaverBrightness.takeIf { keepsDisplayAwake }
                ?: return dashboard
            return minOf(dashboard ?: screensaver, screensaver)
        }
    val camera get() = (viewer as? PanelViewer.Camera)?.id
    val cameraOpenedAt get() = (viewer as? PanelViewer.Camera)?.openedAt ?: 0L
    val musicOpen get() = (viewer as? PanelViewer.Music)?.id
    val agendaOpen get() = (viewer as? PanelViewer.Agenda)?.id
    val contentOpen get() = (viewer as? PanelViewer.Content)?.id
    val hasViewer get() = viewer != null
    val showingDashboard get() = configured && !clock && !screenBlank && !editor && !unlocking && !hasViewer

    fun entity(id: String) = entities[id] ?: catalogue.entity(id)
    fun entityName(id: String) = entity(id)?.name ?: id.substringAfter('.').replace('_', ' ')
    fun label(spec: TileSpec) = spec.label ?: entityName(spec.entity)

    fun wakeDisplay(): PanelUi {
        if (!configured || (!clock && !screenBlank) || editor || unlocking || hasViewer) return this
        return copy(clock = false, screenBlank = false, page = Page.FAVOURITES)
    }

    fun showCamera(id: String, openedAt: Long): PanelUi {
        if (camera == id || config.favourites.none { it.entity == id && it.kind == TileKind.CAMERA }) return this
        return showViewer(PanelViewer.Camera(id, openedAt))
    }

    fun showViewer(viewer: PanelViewer): PanelUi = copy(viewer = viewer, screenBlank = false, clock = false)

    private fun closeViewer(): PanelUi = if (hasViewer) copy(viewer = null) else this

    private fun openTile(id: String, openedAt: Long): PanelUi {
        val kind = config.favourites.firstOrNull { it.entity == id }?.kind ?: return this
        return when {
            kind == TileKind.CAMERA -> showCamera(id, openedAt)
            kind == TileKind.MEDIA -> showViewer(PanelViewer.Music(id))
            kind.isAgenda -> showViewer(PanelViewer.Agenda(id))
            kind.hasContentScreen -> showViewer(PanelViewer.Content(id))
            else -> this
        }
    }

    internal val acceptsRemoteCommands: Boolean
        get() = configured && link == Link.ONLINE && remoteName != null && !editor && !unlocking

    fun remoteCommand(command: PanelCommand, openedAt: Long): PanelUi {
        if (!acceptsRemoteCommands) return this
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
            is PanelCommand.LaunchApp -> this
        }
    }

    val message: String? get() = displayMessage(config.messageEntity)
    val screensaverMessage: String? get() = displayMessage(config.screensaverMessageEntity)

    private fun displayMessage(id: String?): String? = if (link != Link.ONLINE) null else id?.let(entities::get)
        ?.takeIf { it.available && it.state !in emptyMessages }
        ?.state?.replace(whitespace, " ")?.trim()?.take(120)?.takeIf(String::isNotEmpty)

    fun activeHomeBadges(): List<ActiveHomeBadge> {
        if (link != Link.ONLINE) return emptyList()
        return config.homeBadges.mapNotNull { badge ->
            val entity = entities[badge.entity]?.takeIf { it.available && it.state.trim().lowercase() in badge.activeStates }
                ?: return@mapNotNull null
            ActiveHomeBadge(badge.label ?: entity.name, entity)
        }
    }
}

internal fun PanelUi.liveEntityIds(roomsRequested: Boolean): Set<String> = buildSet {
    for (tile in config.favourites) {
        if (tile.kind != TileKind.APP) add(tile.entity)
        tile.statusEntity?.let(::add)
        tile.cameraActions.forEach { add(it.entity) }
        tile.cameraStream?.let(::add)
    }
    if (!screenBlank && (roomsRequested || config.roomIds?.isNotEmpty() == true)) {
        roomSections.forEach { section -> section.tiles.mapTo(this) { it.entity } }
    }
    addAll(pending.keys)
    config.messageEntity?.let(::add)
    config.screensaverMessageEntity?.let(::add)
    config.homeBadges.mapTo(this) { it.entity }
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
