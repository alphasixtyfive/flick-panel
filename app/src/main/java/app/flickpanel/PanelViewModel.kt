package app.flickpanel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelStore
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.config.validPin
import app.flickpanel.ha.Area
import app.flickpanel.ha.Catalogue
import app.flickpanel.ha.Entity
import app.flickpanel.ha.GraphPoint
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.HaProblem
import app.flickpanel.ha.Link
import app.flickpanel.ha.parseServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.math.abs

internal enum class Page(val label: String) { FAVOURITES("Favourites"), ROOMS("Rooms"), CAMERAS("Cameras") }

internal data class PanelUi(
    val loading: Boolean = true,
    val configured: Boolean = false,
    val config: PanelConfig = PanelConfig(),
    val link: Link = Link.OFFLINE,
    val entities: Map<String, Entity> = emptyMap(),
    val catalogue: Catalogue = Catalogue(),
    val loadingCatalogue: Boolean = false,
    val page: Page = Page.FAVOURITES,
    val room: String? = null,
    val clock: Boolean = false,
    val camera: String? = null,
    val unlocking: Boolean = false,
    val editor: Boolean = false,
    val connectionEditor: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val pending: Map<String, String> = emptyMap(),
    val graphs: Map<String, List<GraphPoint>> = emptyMap()
) {
    val rooms: List<Area> get() = catalogue.areas.filter { catalogue.room(it.id).isNotEmpty() }
    val roomTiles: List<TileSpec> get() = (room ?: rooms.firstOrNull()?.id)?.let { area ->
        catalogue.room(area).take(32).map { TileSpec(it.entity.id, it.entity.kinds().first()) }
    }.orEmpty()
    fun entity(id: String) = entities[id] ?: catalogue.entries.firstOrNull { it.entity.id == id }?.entity
    fun label(spec: TileSpec) = spec.label ?: entity(spec.entity)?.name ?: spec.entity.substringAfter('.').replace('_', ' ')

    val message: String? get() = if (link != Link.ONLINE) null else config.messageEntity?.let(entities::get)
        ?.takeIf { it.available && it.state !in setOf("on", "off", "none", "No reminders") }
        ?.state?.replace(Regex("\\s+"), " ")?.trim()?.take(120)?.takeIf(String::isNotEmpty)
}

internal class PanelViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PanelStore(application)
    private val _ui = MutableStateFlow(PanelUi())
    val ui = _ui.asStateFlow()
    private var client: HaClient? = null
    private var linkJob: Job? = null
    private var stateJob: Job? = null
    private var catalogueJob: Job? = null
    private var idleJob: Job? = null
    private var editorTimer: Job? = null
    private var approachJob: Job? = null
    private val graphTimes = mutableMapOf<String, Long>()
    private val graphRequests = mutableSetOf<String>()
    private var foreground = false
    private var securityEpoch = 0
    private var presence: Boolean? = null

    init {
        viewModelScope.launch {
            safely {
                val saved = withContext(Dispatchers.IO) { store.read() }
                _ui.update { it.copy(loading = false, configured = saved.configured, config = saved.config) }
                if (saved.credentials != null) setupClient(saved.credentials)
            }
            _ui.update { it.copy(loading = false) }
            touch()
        }
    }

    fun visible(value: Boolean) {
        foreground = value
        if (value) {
            client?.start()
            touch()
        } else {
            idleJob?.cancel()
            approachJob?.cancel()
            client?.stop()
            presence = null
            relock()
            _ui.update { it.copy(camera = null, pending = emptyMap()) }
        }
    }

    fun touch() {
        if (!foreground || !_ui.value.configured || _ui.value.clock) return
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(_ui.value.config.idleSeconds * 1000L)
            val current = _ui.value
            if (foreground && !current.editor && !current.unlocking && current.camera == null &&
                presence != true) _ui.update { it.copy(clock = true) }
        }
    }

    fun wake() {
        _ui.update { it.copy(clock = false) }
        selectPage(Page.FAVOURITES)
    }

    fun selectPage(page: Page) {
        _ui.update { it.copy(page = page) }
        if (page == Page.ROOMS && _ui.value.catalogue.areas.isEmpty()) loadCatalogue()
        updateSelection()
        touch()
    }

    fun selectRoom(id: String) {
        if (_ui.value.rooms.none { it.id == id }) return
        _ui.update { it.copy(room = id) }
        updateSelection()
        touch()
    }

    fun showUnlock() {
        if (_ui.value.configured && !_ui.value.editor) _ui.update { it.copy(unlocking = true, error = null) }
    }

    fun unlock(pin: String) {
        if (!_ui.value.unlocking || _ui.value.busy) return
        val epoch = securityEpoch
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            safely {
                if (!validPin(pin)) throw HaProblem("Enter your 4 to 8 digit PIN")
                if (!withContext(Dispatchers.IO) { store.verify(pin) }) throw HaProblem("Incorrect setup PIN")
                if (epoch == securityEpoch && foreground) openEditor()
            }
            _ui.update { it.copy(busy = false) }
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

    fun connect(address: String, token: String, pin: String, confirmation: String) {
        val current = _ui.value
        if (current.busy || (current.configured && !current.editor)) return
        val epoch = securityEpoch
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            safely {
                if (!current.configured && (!validPin(pin) || pin != confirmation)) {
                    throw HaProblem("Enter the same 4 to 8 digit setup PIN twice")
                }
                val secret = token.trim()
                if (secret.length !in 16..4096 || secret.any(Char::isWhitespace)) {
                    throw HaProblem("Paste a Home Assistant long-lived access token")
                }
                val credentials = HaCredentials(parseServer(address), secret)
                HaClient.validate(credentials)
                if (epoch != securityEpoch || !foreground) return@safely
                withContext(Dispatchers.IO) {
                    store.connect(credentials, current.config, if (current.configured) null else pin)
                }
                _ui.update { it.copy(configured = true, connectionEditor = false, clock = false, error = null) }
                setupClient(credentials)
                if (epoch == securityEpoch && foreground) openEditor()
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun save(config: PanelConfig) {
        if (!_ui.value.editor || _ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            safely {
                withContext(Dispatchers.IO) { store.save(config) }
                _ui.update { it.copy(config = config) }
                updateSelection()
                relock()
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun clearError() { _ui.update { it.copy(error = null) } }
    fun openCamera(id: String) {
        if (_ui.value.config.favourites.any { it.entity == id && it.kind == TileKind.CAMERA }) {
            idleJob?.cancel()
            _ui.update { it.copy(camera = id) }
        }
    }
    fun closeCamera() { _ui.update { it.copy(camera = null) }; touch() }

    fun toggle(entity: Entity, checked: Boolean) = send(entity, if (checked) "turn_on" else "turn_off",
        pending = if (checked) "On" else "Off", matches = { it.state == if (checked) "on" else "off" })

    fun dim(entity: Entity, level: Int) {
        val value = level.coerceIn(0, 100)
        if (value == 0) toggle(entity, false)
        else send(entity, "turn_on", JSONObject().put("brightness_pct", value),
            pending = "$value%", matches = { state -> state.brightness?.let { abs(it - value) <= 1 } == true })
    }

    fun setTemperature(entity: Entity, value: Double) {
        val min = entity.attributes.optDouble("min_temp", 5.0)
        val max = entity.attributes.optDouble("max_temp", 35.0)
        if (value !in min..max) return
        send(entity, "set_temperature", JSONObject().put("temperature", value), pending = "$value°",
            matches = { abs(it.attributes.optDouble("temperature") - value) < .05 })
    }

    fun action(entity: Entity) {
        when (entity.domain) {
            "button", "input_button" -> send(entity, "press")
            "scene", "script" -> send(entity, "turn_on")
        }
    }

    private fun send(
        entity: Entity, service: String, payload: JSONObject = JSONObject(), pending: String = "Sending…",
        matches: ((Entity) -> Boolean)? = null
    ) {
        val active = client ?: return
        if (_ui.value.link != Link.ONLINE || !entity.available || entity.id in _ui.value.pending) return
        _ui.update { it.copy(pending = it.pending + (entity.id to pending)) }
        viewModelScope.launch {
            try {
                safely {
                    active.command(entity.id, entity.domain, service, payload)
                    if (matches != null) {
                        val reported = withTimeoutOrNull(4_500) {
                            active.entities.first { it[entity.id]?.let(matches) == true }
                        }
                        if (reported == null) throw HaProblem("Home Assistant hasn't confirmed the change")
                    }
                }
            } finally { _ui.update { it.copy(pending = it.pending - entity.id) } }
        }
    }

    suspend fun snapshot(id: String): Bitmap? {
        val data = client?.snapshot(id) ?: return null
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val edge = maxOf(bounds.outWidth, bounds.outHeight)
            if (edge !in 1..12_000) return@withContext null
            val options = BitmapFactory.Options().apply {
                while (edge / inSampleSize.coerceAtLeast(1) > 1440) {
                    inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                }
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        }
    }
    suspend fun stream(id: String): HttpUrl = client?.stream(id) ?: throw HaProblem("Home Assistant isn't connected")
    fun videoTransport(): OkHttpClient = client?.videoClient() ?: throw HaProblem("Home Assistant isn't connected")

    fun loadGraph(id: String) {
        val active = client ?: return
        val now = SystemClock.elapsedRealtime()
        if (graphTimes[id]?.let { now - it < 60_000 } == true || !graphRequests.add(id)) return
        viewModelScope.launch {
            try {
                val values = active.history(id)
                if (client === active) {
                    graphTimes[id] = now
                    _ui.update { it.copy(graphs = it.graphs + (id to values)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { graphTimes[id] = now }
            finally { graphRequests.remove(id) }
        }
    }

    fun loadCatalogue() {
        val active = client ?: return
        if (catalogueJob?.isActive == true || _ui.value.link != Link.ONLINE) return
        catalogueJob = viewModelScope.launch {
            _ui.update { it.copy(loadingCatalogue = true) }
            try {
                safely {
                    val output = withContext(Dispatchers.Default) { active.catalogue() }
                    if (client === active) {
                        _ui.update { it.copy(catalogue = output) }
                        updateSelection()
                    }
                }
            } finally { _ui.update { it.copy(loadingCatalogue = false) } }
        }
    }

    private fun setupClient(credentials: HaCredentials) {
        linkJob?.cancel()
        stateJob?.cancel()
        catalogueJob?.cancel()
        client?.stop()
        graphTimes.clear()
        graphRequests.clear()
        presence = null
        _ui.update { it.copy(entities = emptyMap(), catalogue = Catalogue(), graphs = emptyMap(), pending = emptyMap(), link = Link.CONNECTING) }
        val active = HaClient(credentials, viewModelScope)
        client = active
        updateSelection()
        linkJob = viewModelScope.launch {
            active.link.collect { state ->
                _ui.update { it.copy(link = state) }
                if (state == Link.OFFLINE || state == Link.AUTH_REJECTED) {
                    approachJob?.cancel()
                    if (presence == true) { presence = null; touch() }
                }
                if (state == Link.ONLINE && (_ui.value.editor || _ui.value.page == Page.ROOMS) &&
                    _ui.value.catalogue.entries.isEmpty()) loadCatalogue()
            }
        }
        stateJob = viewModelScope.launch {
            active.entities.collect { states ->
                val current = _ui.value
                val nextPresence = current.config.presenceEntity?.let(states::get)
                    ?.takeIf(Entity::available)?.state?.let { it == "on" }
                _ui.update { it.copy(entities = states) }
                val before = presence
                presence = nextPresence
                if (foreground && nextPresence == true && before != true && current.clock) {
                    approachJob?.cancel()
                    approachJob = viewModelScope.launch {
                        delay(700)
                        if (foreground && presence == true && _ui.value.clock && _ui.value.link == Link.ONLINE) wake()
                    }
                }
                if (before == true && nextPresence != true) touch()
            }
        }
        if (foreground) active.start()
    }

    private fun updateSelection() {
        val current = _ui.value
        val ids = current.config.favourites.mapTo(mutableSetOf()) { it.entity }
        current.config.favourites.mapNotNullTo(ids) { it.statusEntity }
        if (current.page == Page.ROOMS) current.roomTiles.mapTo(ids) { it.entity }
        current.config.presenceEntity?.let(ids::add)
        current.config.messageEntity?.let(ids::add)
        client?.select(ids)
    }

    private suspend inline fun safely(block: () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val message = (error as? HaProblem)?.message ?: (error as? IllegalArgumentException)?.message
                ?: "Flick Panel couldn't complete this request"
            _ui.update { it.copy(error = message) }
        }
    }
}
