package app.flickpanel

import android.app.ActivityManager
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelBackup
import app.flickpanel.config.PanelBackupAction
import app.flickpanel.config.PanelStore
import app.flickpanel.config.PanelStoreProblem
import app.flickpanel.config.SavedRemote
import app.flickpanel.config.TileKind
import app.flickpanel.config.validPin
import app.flickpanel.ha.CameraStream
import app.flickpanel.ha.CoverAction
import app.flickpanel.ha.Entity
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.HaProblem
import app.flickpanel.ha.MEDIA_SEEK
import app.flickpanel.ha.Link
import app.flickpanel.ha.MEDIA_VOLUME_SET
import app.flickpanel.ha.MEDIA_VOLUME_MUTE
import app.flickpanel.ha.MediaChoice
import app.flickpanel.ha.MediaTransport
import app.flickpanel.ha.PanelCommand
import app.flickpanel.ha.PanelRegistration
import app.flickpanel.ha.PanelRemoteState
import app.flickpanel.ha.PhotoAlbum
import app.flickpanel.ha.adjacentTemperature
import app.flickpanel.ha.canAddTasks
import app.flickpanel.ha.canMoveCover
import app.flickpanel.ha.canUpdateTasks
import app.flickpanel.ha.coverPosition
import app.flickpanel.ha.gateToggleAction
import app.flickpanel.ha.hasMediaFeature
import app.flickpanel.ha.parseServer
import app.flickpanel.ha.playbackProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

internal class PanelViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PanelStore(application)
    private val _ui = MutableStateFlow(PanelUi())
    val ui = _ui.asStateFlow()
    val agenda = PanelAgenda(viewModelScope)
    private var client: HaClient? = null
    private var linkJob: Job? = null
    private var stateJob: Job? = null
    private var remoteJob: Job? = null
    private var telemetryJob: Job? = null
    private var catalogueJob: Job? = null
    private var idleJob: Job? = null
    private var idleDeadline = 0L
    private var editorTimer: Job? = null
    private val commands = DeviceCommandQueue(viewModelScope, _ui, { client.takeIf { foreground } }, ::reportFailure)
    private val graphTimes = mutableMapOf<String, Long>()
    private val graphRequests = mutableSetOf<String>()
    private val lowImageMemory = application.getSystemService(ActivityManager::class.java)
        ?.let { it.isLowRamDevice || it.memoryClass <= 128 } == true
    private var images = PanelImages(viewModelScope, lowImageMemory)
    private data class MusicFolder(val player: String, val type: String?, val id: String?)
    private data class MusicPage(val choices: List<MediaChoice>, val fetchedAt: Long)
    private val musicPages = LruCache<MusicFolder, MusicPage>(16)
    private var foreground = false
    private var roomControlsRequested = false
    private var securityEpoch = 0
    private var pendingBackup: Pair<PanelBackupAction, PanelConfig>? = null

    init {
        viewModelScope.launch {
            safely {
                val saved = withContext(Dispatchers.IO) { store.read() }
                _ui.update { it.copy(
                    loading = false,
                    configured = saved.configured,
                    layout = RoomLayout(saved.config),
                    error = if (saved.layoutUnreadable) "Saved layout couldn't be read. Create a new layout in Setup." else null,
                ) }
                if (saved.credentials != null) setupClient(saved.credentials, saved.remote)
            }
            _ui.update { it.copy(loading = false) }
            touch()
        }
    }

    fun visible(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) {
            client?.start()
            touch()
        } else {
            cancelIdle()
            cancelCatalogueLoad()
            commands.cancel()
            client?.stop()
            agenda.pause()
            relock()
            _ui.update { it.copy(viewer = null, screenBlank = false) }
        }
    }

    fun touch() {
        val current = _ui.value
        if (!foreground || !current.showingDashboard || current.config.idleSeconds == 0) return
        idleDeadline = SystemClock.elapsedRealtime() + current.config.idleSeconds * 1_000L
        if (idleJob?.isActive == true) return
        idleJob = viewModelScope.launch {
            while (true) {
                val remaining = idleDeadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                delay(remaining)
            }
            idleJob = null
            if (foreground && _ui.value.showingDashboard) {
                _ui.update { it.copy(clock = true) }
            }
        }
    }

    private fun cancelIdle() {
        idleJob?.cancel()
        idleJob = null
    }

    fun wake() {
        val current = _ui.value
        val next = current.wakeDisplay()
        if (next === current) return
        _ui.value = next
        touch()
    }

    fun showIdleScreen() {
        if (!_ui.value.showingDashboard) return
        cancelIdle()
        _ui.update { it.copy(clock = true) }
    }

    fun selectPage(page: Page) {
        _ui.update { it.copy(page = page) }
        if (page == Page.ROOMS) showRoomControls()
        touch()
    }

    private fun showRoomControls() {
        if (!roomControlsRequested) {
            roomControlsRequested = true
            updateSelection()
        }
        if (_ui.value.catalogue.entries.isEmpty()) loadCatalogue()
    }

    fun showUnlock() {
        if (!_ui.value.showingDashboard) return
        cancelIdle()
        _ui.update { it.copy(unlocking = true, error = null) }
    }

    fun unlock(pin: String) {
        if (!_ui.value.unlocking) return
        val epoch = securityEpoch
        submitSettingsOperation {
            if (!validPin(pin)) throw HaProblem("Enter your 4 to 8 digit PIN")
            if (!withContext(Dispatchers.IO) { store.verify(pin) }) throw HaProblem("Incorrect setup PIN")
            if (epoch == securityEpoch && foreground) openEditor()
        }
    }

    fun relock() {
        securityEpoch++
        editorTimer?.cancel()
        _ui.update { it.copy(unlocking = false, editor = false, connectionEditor = false, error = null) }
        touch()
    }

    private fun openEditor() {
        _ui.update { it.copy(unlocking = false, editor = true, error = null) }
        editorTimer?.cancel()
        editorTimer = viewModelScope.launch { delay(5 * 60_000L); relock() }
        if (_ui.value.catalogue.entries.isEmpty()) loadCatalogue()
    }

    fun changeConnection() {
        if (_ui.value.editor) _ui.update { it.copy(connectionEditor = true, error = null) }
    }

    fun cancelConnectionChange() {
        _ui.update { if (it.connectionEditor) it.copy(connectionEditor = false, error = null) else it }
    }

    fun linkPanel(name: String) {
        val active = client ?: return
        val current = _ui.value
        if (!foreground || !current.editor || current.link != Link.ONLINE ||
            current.remoteName != null && current.remoteState != PanelRemoteState.UNAVAILABLE) return
        submitSettingsOperation {
            val label = name.trim()
            if (label.isEmpty() || label.length > 48 || label.any(Char::isISOControl)) {
                throw HaProblem("Enter a tablet name of up to 48 characters")
            }
            val device = withContext(Dispatchers.IO) { store.deviceId() }
            val id = active.registerPanel(PanelRegistration(
                device, label, BuildConfig.VERSION_NAME, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
            ))
            withContext(Dispatchers.IO) { store.saveRemote(SavedRemote(id, label)) }
            _ui.update { it.copy(remoteName = label, remoteState = PanelRemoteState.CONNECTING) }
            bindRemote(active, id)
        }
    }

    fun unlinkPanel() {
        val current = _ui.value
        if (!current.editor || current.remoteName == null) return
        submitSettingsOperation {
            withContext(Dispatchers.IO) { store.clearRemote() }
            _ui.update { it.copy(
                remoteName = null, remoteState = PanelRemoteState.DISABLED,
                brightnessPercent = null, keepAwakeOverride = null,
            ) }
            client?.let { bindRemote(it, null) }
        }
    }

    fun connect(address: String, token: String, pin: String, confirmation: String) {
        val current = _ui.value
        if (current.configured && !current.editor) return
        val epoch = securityEpoch
        submitSettingsOperation {
            if (!current.configured && (!validPin(pin) || pin != confirmation)) {
                throw HaProblem("Enter the same 4 to 8 digit setup PIN twice")
            }
            val secret = token.trim()
            if (secret.length !in 16..4096 || secret.any(Char::isWhitespace)) {
                throw HaProblem("Paste a Home Assistant long-lived access token")
            }
            val credentials = HaCredentials(parseServer(address), secret)
            HaClient.validate(credentials)
            if (epoch != securityEpoch || !foreground) return@submitSettingsOperation
            val remote = withContext(Dispatchers.IO) {
                store.connect(credentials, current.config, if (current.configured) null else pin)
            }
            _ui.update { it.copy(configured = true, connectionEditor = false, clock = false, error = null) }
            setupClient(credentials, remote)
            if (epoch == securityEpoch && foreground) openEditor()
        }
    }

    fun save(config: PanelConfig) {
        if (!_ui.value.editor) return
        submitSettingsOperation {
            saveLayout(config)
            relock()
        }
    }

    fun beginBackup(action: PanelBackupAction): Boolean {
        val current = _ui.value
        if (!foreground || !current.editor || current.busy || pendingBackup != null) return false
        pendingBackup = action to current.config
        return true
    }

    fun backupDocument(action: PanelBackupAction, uri: Uri?, done: (String) -> Unit) {
        val requested = pendingBackup?.takeIf { it.first == action } ?: return
        pendingBackup = null
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        submitSettingsOperation {
            when (action) {
                PanelBackupAction.BACK_UP -> {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri, "wt")?.use { PanelBackup.write(requested.second, it) }
                            ?: throw PanelStoreProblem("Couldn't save the backup")
                    }
                    done("Backup saved")
                }
                PanelBackupAction.RESTORE -> {
                    val config = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use(PanelBackup::read)
                            ?: throw PanelStoreProblem("Couldn't open the backup")
                    }
                    saveLayout(config)
                    relock()
                    done("Backup restored")
                }
            }
        }
    }

    private suspend fun saveLayout(config: PanelConfig) {
        withContext(Dispatchers.IO) { store.save(config) }
        _ui.update { it.copy(layout = it.layout.copy(config = config)) }
        updateSelection()
    }

    private fun submitSettingsOperation(operation: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                safely { operation() }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun openCamera(id: String) {
        val current = _ui.value
        if (!current.showingDashboard) return
        val next = current.showCamera(id, SystemClock.elapsedRealtime())
        if (next === current) return
        cancelIdle()
        _ui.value = next
    }

    fun closeCamera() = closeViewer<PanelViewer.Camera>()

    fun openCameraGate(camera: String) {
        val current = _ui.value
        if (current.camera != camera) return
        val gateId = current.config.favourites.firstOrNull { it.entity == camera && it.kind == TileKind.CAMERA }?.cameraGate ?: return
        val gate = current.entities[gateId]?.takeIf { it.state == "closed" && it.gateWithOpenControl } ?: return
        moveCover(gate, CoverAction.OPEN)
    }

    fun openMusic(id: String) {
        val current = _ui.value
        if (!current.showingDashboard) return
        if (current.link != Link.ONLINE || current.entities[id]?.available != true) return
        val availableTiles = if (current.page == Page.ROOMS) current.roomSections.asSequence().flatMap { it.tiles.asSequence() }
            else current.config.favourites.asSequence()
        if (availableTiles.none { it.entity == id && it.kind == TileKind.MEDIA }) return
        openViewer(PanelViewer.Music(id))
    }

    fun closeMusic() = closeViewer<PanelViewer.Music>()

    fun openContent(id: String) {
        val current = _ui.value
        if (!current.showingDashboard || current.config.favourites.none { it.entity == id && it.kind.hasContentScreen }) return
        openViewer(PanelViewer.Content(id))
    }

    fun closeContent() = closeViewer<PanelViewer.Content>()

    fun loadAgenda(id: String, force: Boolean = false) {
        val active = client ?: return
        val tile = agendaTile(id) ?: return
        val current = _ui.value
        if (!foreground || current.link != Link.ONLINE || current.entities[id]?.available != true) return
        agenda.refresh(active, tile, force)
    }

    fun openAgenda(id: String) {
        if (!_ui.value.showingDashboard || agendaTile(id) == null) return
        openViewer(PanelViewer.Agenda(id))
        loadAgenda(id)
    }

    fun closeAgenda() = closeViewer<PanelViewer.Agenda>()

    private fun openViewer(viewer: PanelViewer) {
        cancelIdle()
        _ui.update { it.showViewer(viewer) }
    }

    private inline fun <reified T : PanelViewer> closeViewer() {
        if (_ui.value.viewer !is T) return
        _ui.update { it.copy(viewer = null) }
        touch()
    }

    fun completeTask(id: String, uid: String, completed: Boolean) {
        val active = taskClient(id) ?: return
        if (_ui.value.entities[id]?.canUpdateTasks != true) return
        val tasks = (agenda.cached(id)?.content as? AgendaContent.Tasks)?.items ?: return
        val task = tasks.firstOrNull { it.uid == uid && it.completed != completed } ?: return
        val optimistic = AgendaContent.Tasks(tasks.map { if (it.uid == uid) task.copy(completed = completed) else it })
        agenda.changeTasks(active, id, optimistic,
            confirmed = { updated -> updated.any { it.uid == uid && it.completed == completed } },
        ) { setTaskCompleted(id, uid, completed) }
    }

    fun addTask(id: String, summary: String): Boolean {
        val active = taskClient(id) ?: return false
        if (_ui.value.entities[id]?.canAddTasks != true) return false
        val existing = (agenda.cached(id)?.content as? AgendaContent.Tasks)?.items
            ?.mapTo(hashSetOf()) { it.uid }.orEmpty()
        val title = summary.trim()
        return agenda.changeTasks(active, id,
            confirmed = { updated -> updated.any { it.uid !in existing && it.summary == title } },
        ) { addTask(id, title) }
    }

    private fun agendaTile(id: String) = _ui.value.config.favourites.firstOrNull { it.entity == id && it.kind.isAgenda }

    private fun taskClient(id: String): HaClient? {
        val current = _ui.value
        return client.takeIf { foreground && current.link == Link.ONLINE && current.agendaOpen == id &&
            agendaTile(id)?.kind == TileKind.TASKS && current.entities[id]?.available == true }
    }

    suspend fun browseMusic(id: String, folder: MediaChoice?): List<MediaChoice> {
        val key = MusicFolder(id, folder?.type, folder?.id)
        musicPages[key]?.takeIf { SystemClock.elapsedRealtime() - it.fetchedAt < 120_000 }?.let { return it.choices }
        val active = client ?: throw HaProblem("Home Assistant isn't connected")
        return withContext(Dispatchers.Default) { active.browseMusic(id, folder) }.also { choices ->
            if (client === active) musicPages.put(key, MusicPage(choices, SystemClock.elapsedRealtime()))
        }
    }

    fun mediaTransport(id: String, transport: MediaTransport) {
        val entity = _ui.value.entities[id] ?: return
        if (entity.domain != "media_player" || !entity.mediaFeatures.hasMediaFeature(transport.bit)) return
        if (transport == MediaTransport.PREVIOUS || transport == MediaTransport.NEXT) {
            commands.send(entity, transport.service, pending = "Changing track…")
            return
        }
        val play = transport == MediaTransport.PLAY
        if (entity.playingMedia == play) return
        commands.send(entity, transport.service, pending = if (play) "Starting…" else "Pausing…",
            matches = { it.available && it.playingMedia == play })
    }

    fun seekMusic(id: String, seconds: Double) {
        val entity = _ui.value.entities[id] ?: return
        val duration = entity.playbackProgress()?.duration ?: return
        if (!entity.mediaFeatures.hasMediaFeature(MEDIA_SEEK) || !seconds.isFinite()) return
        commands.send(entity, "media_seek", JSONObject().put("seek_position", seconds.coerceIn(0.0, duration)),
            pending = "Seeking…")
    }

    fun setVolume(entity: Entity, percent: Int) {
        if (entity.domain != "media_player" || !entity.mediaFeatures.hasMediaFeature(MEDIA_VOLUME_SET)) return
        val value = percent.coerceIn(0, 100)
        commands.send(entity, "volume_set", JSONObject().put("volume_level", value / 100.0), pending = "$value%",
            matches = { it.volumePercent?.let { reported -> abs(reported - value) <= 1 } == true })
    }

    fun setSpeakerMuted(entity: Entity, muted: Boolean) {
        if (entity.domain != "media_player" || !entity.mediaFeatures.hasMediaFeature(MEDIA_VOLUME_MUTE)) return
        commands.send(entity, "volume_mute", JSONObject().put("is_volume_muted", muted),
            pending = if (muted) "Muting…" else "Unmuting…", matches = { it.volumeMuted == muted })
    }

    suspend fun playMusic(id: String, media: MediaChoice) {
        if (!media.playable || _ui.value.musicOpen != id) return
        val active = client ?: throw HaProblem("Home Assistant isn't connected")
        active.command(id, "media_player", "play_media",
            JSONObject().put("media_content_type", media.type).put("media_content_id", media.id))
    }

    fun toggle(entity: Entity, checked: Boolean) = commands.send(entity, if (checked) "turn_on" else "turn_off",
        pending = if (checked) "On" else "Off", matches = { it.state == if (checked) "on" else "off" })

    fun dim(entity: Entity, level: Int) {
        val value = level.coerceIn(0, 100)
        if (value == 0) toggle(entity, false)
        else commands.send(entity, "turn_on", JSONObject().put("brightness_pct", value),
            pending = "$value%", matches = { state -> state.brightness?.let { abs(it - value) <= 1 } == true })
    }

    fun stepTemperature(id: String, increase: Boolean) {
        val entity = _ui.value.entities[id] ?: return
        val value = entity.adjacentTemperature(increase) ?: return
        commands.send(entity, "set_temperature", JSONObject().put("temperature", value), pending = "$value°",
            matches = { abs(it.attributes.optDouble("temperature") - value) < .05 })
    }

    fun action(entity: Entity) {
        when (entity.domain) {
            "button", "input_button" -> commands.send(entity, "press")
            "scene", "script" -> commands.send(entity, "turn_on")
        }
    }

    fun controlCover(id: String, action: CoverAction) {
        val state = _ui.value
        if (state.config.favourites.none { it.entity == id && it.kind == TileKind.COVER }) return
        val cover = state.entities[id] ?: return
        if (cover.isGate && cover.gateToggleAction(state.pending[id]) != action) return
        moveCover(cover, action)
    }

    private fun moveCover(cover: Entity, action: CoverAction) {
        if (!cover.canMoveCover(action, _ui.value.pending[cover.id])) return
        val matches: ((Entity) -> Boolean)? = when (action) {
            CoverAction.OPEN -> { result ->
                result.state == "opening" || result.state == "open" && (result.coverPosition() ?: 100) == 100
            }
            CoverAction.CLOSE -> { result -> result.state == "closing" || result.state == "closed" }
            CoverAction.STOP -> null
        }
        commands.send(cover, action.service, pending = action.progress, matches = matches, interruptPending = action == CoverAction.STOP)
    }

    fun selectOption(entity: Entity, option: String) {
        val current = _ui.value.entities[entity.id] ?: return
        if (option == current.state || option !in current.options) return
        commands.send(current, "select_option", JSONObject().put("option", option), pending = option, matches = { it.state == option })
    }

    fun stepNumber(entity: Entity, increase: Boolean) {
        val current = _ui.value.entities[entity.id] ?: return
        val value = current.numberValue ?: return
        val next = current.numberRange?.adjacent(value, increase) ?: return
        val label = listOf(next.toPlainString(), current.unit).filter(String::isNotEmpty).joinToString(" ")
        commands.send(current, "set_value", JSONObject().put("value", next.toDouble()), pending = label,
            matches = { it.numberValue?.compareTo(next) == 0 })
    }

    fun cachedSnapshot(id: String, revision: String = ""): Bitmap? = images.snapshots.cached("$id@$revision")

    suspend fun snapshot(id: String, revision: String = ""): Bitmap? {
        if (!foreground) return null
        val active = client ?: return null
        return images.snapshots.load("$id@$revision") { active.snapshot(id) }
    }

    fun cachedArtwork(reference: String): Bitmap? = images.artwork.fresh(reference)

    suspend fun browsePhotos(id: String?): PhotoAlbum {
        val active = client ?: throw HaProblem("Home Assistant isn't connected")
        return withContext(Dispatchers.Default) { active.photoAlbum(id) }
    }

    suspend fun loadPhoto(id: String): Bitmap? {
        val active = client ?: return null
        return images.photos.load(id) { active.photo(id) }
    }

    suspend fun loadArtwork(reference: String): Bitmap? {
        val active = client ?: return null
        return images.artwork.load(reference) { active.artwork(reference) }
    }

    fun cachedPersonAvatar(reference: String): Bitmap? = images.artwork.fresh("person:$reference")

    suspend fun loadPersonAvatar(reference: String): Bitmap? {
        if (!foreground) return null
        val active = client ?: return null
        return images.artwork.load("person:$reference", decodeEdge = 192) { active.personAvatar(reference) }
    }

    suspend fun cameraStream(id: String): CameraStream {
        val active = client ?: throw HaProblem("Home Assistant isn't connected")
        val stream = active.cameraStream(id)
        if (client !== active) throw HaProblem("The Home Assistant connection changed")
        return stream
    }

    suspend fun loadGraph(id: String) {
        if (!foreground) return
        val active = client ?: return
        val now = SystemClock.elapsedRealtime()
        if (graphTimes[id]?.let { now - it < 60_000 } == true || !graphRequests.add(id)) return
        try {
            val values = withContext(Dispatchers.Default) { active.history(id) }
            if (client === active && _ui.value.config.favourites.any { it.kind == TileKind.TREND && it.entity == id }) {
                graphTimes[id] = now
                _ui.update { it.copy(graphs = it.graphs + (id to values)) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (client === active) graphTimes[id] = now
        } finally {
            if (client === active) graphRequests.remove(id)
        }
    }

    fun loadCatalogue() {
        val active = client ?: return
        if (!foreground || catalogueJob != null || _ui.value.link != Link.ONLINE) return
        val request = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                safely {
                    val output = withContext(Dispatchers.Default) { active.catalogue() }
                    if (client === active) {
                        _ui.update { it.copy(layout = it.layout.copy(catalogue = output)) }
                        updateSelection()
                    }
                }
            } finally {
                if (catalogueJob === coroutineContext[Job]) {
                    catalogueJob = null
                    _ui.update { it.copy(loadingCatalogue = false) }
                }
            }
        }
        catalogueJob = request
        _ui.update { it.copy(loadingCatalogue = true) }
        request.start()
    }

    private fun cancelCatalogueLoad() {
        val request = catalogueJob ?: return
        catalogueJob = null
        request.cancel()
        _ui.update { it.copy(loadingCatalogue = false) }
    }

    private fun setupClient(credentials: HaCredentials, remote: SavedRemote? = null) {
        linkJob?.cancel()
        stateJob?.cancel()
        remoteJob?.cancel()
        telemetryJob?.cancel()
        cancelCatalogueLoad()
        commands.cancel()
        agenda.keep(emptySet())
        client?.stop()
        graphTimes.clear()
        graphRequests.clear()
        images.close()
        images = PanelImages(viewModelScope, lowImageMemory)
        musicPages.evictAll()
        roomControlsRequested = _ui.value.page == Page.ROOMS
        _ui.update { it.copy(
            entities = emptyMap(), layout = RoomLayout(it.config), graphs = emptyMap(),
            link = Link.CONNECTING, serverAddress = credentials.server.toString(),
            remoteName = remote?.name,
            brightnessPercent = null,
            keepAwakeOverride = null,
            remoteState = if (remote == null) PanelRemoteState.DISABLED else PanelRemoteState.CONNECTING,
        ) }
        val active = HaClient(credentials, viewModelScope)
        client = active
        bindRemote(active, remote?.webhookId)
        updateSelection()
        linkJob = viewModelScope.launch {
            active.link.collect { state ->
                _ui.update { it.copy(link = state) }
                val current = _ui.value
                if (state == Link.ONLINE && current.catalogue.entries.isEmpty() &&
                    (current.editor || current.page == Page.ROOMS || current.config.roomIds?.isNotEmpty() == true)
                ) loadCatalogue()
            }
        }
        stateJob = viewModelScope.launch {
            active.entities.collect { states ->
                val current = _ui.value
                _ui.update { it.copy(entities = states) }
                val list = current.agendaOpen?.takeIf { it.startsWith("todo.") }
                if (list != null && current.entities[list]?.state != states[list]?.state) loadAgenda(list, force = true)
            }
        }
        remoteJob = viewModelScope.launch {
            launch {
                active.panelRemoteState.collect { status ->
                    if (client === active) _ui.update { it.copy(remoteState = status) }
                }
            }
            active.panelCommands.collect { command ->
                if (client === active) applyRemoteCommand(command)
            }
        }
        if (foreground) active.start()
    }

    private fun bindRemote(active: HaClient, webhookId: String?) {
        telemetryJob?.cancel()
        active.setPanelWebhook(webhookId)
        telemetryJob = webhookId?.let { id ->
            viewModelScope.launch {
                publishPanelTelemetry(getApplication<Application>(), active, id, _ui)
            }
        }
    }

    private fun applyRemoteCommand(command: PanelCommand) {
        if (!foreground) return
        val current = _ui.value
        if (command is PanelCommand.LaunchApp) {
            if (!current.acceptsRemoteCommands) return
            if (current.config.favourites.none { it.appPackage == command.packageName }) return
            runCatching { getApplication<Application>().openPanelApp(command.packageName) }
                .onFailure { _ui.update { it.copy(error = "This app couldn't open") } }
            return
        }
        val next = current.remoteCommand(command, SystemClock.elapsedRealtime())
        if (next == current) return
        _ui.value = next
        if (command is PanelCommand.SetBrightness || command is PanelCommand.KeepAwake) return
        if (next.page == Page.ROOMS && !next.hasViewer && !next.clock && !next.screenBlank) showRoomControls()
        if (next.showingDashboard) touch() else cancelIdle()
    }

    private fun updateSelection() {
        val current = _ui.value
        val agendas = mutableSetOf<String>()
        val trends = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        for (tile in current.config.favourites) {
            if (tile.kind.isAgenda) agendas += tile.entity
            if (tile.kind == TileKind.TREND) trends += tile.entity
            if (tile.kind != TileKind.APP) ids += tile.entity
            tile.statusEntity?.let(ids::add)
            tile.cameraGate?.let(ids::add)
            tile.cameraStream?.let(ids::add)
        }
        agenda.keep(agendas)
        graphTimes.keys.retainAll(trends)
        if (!trends.containsAll(current.graphs.keys)) {
            _ui.update { it.copy(graphs = it.graphs.filterKeys(trends::contains)) }
        }
        if (current.config.roomIds?.isNotEmpty() == true || roomControlsRequested) {
            current.roomSections.forEach { section -> section.tiles.mapTo(ids) { it.entity } }
        }
        current.config.messageEntity?.let(ids::add)
        current.config.homeBadges.mapTo(ids) { it.entity }
        client?.select(ids)
    }

    private suspend inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFailure(error)
        }
    }

    private fun reportFailure(error: Exception) {
        val message = when (error) {
            is HaProblem, is PanelStoreProblem, is IllegalArgumentException -> error.message
            else -> null
        } ?: "Flick Panel couldn't complete this request"
        _ui.update { it.copy(error = message) }
    }
}
