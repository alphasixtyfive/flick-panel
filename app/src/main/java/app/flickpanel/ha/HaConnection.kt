package app.flickpanel.ha

import app.flickpanel.config.validEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

private const val FIRST_RETRY_DELAY_MS = 750L
private const val MAX_RETRY_DELAY_MS = 15_000L

internal class HaConnection(
    private val credentials: HaCredentials,
    private val scope: CoroutineScope,
) {
    private val lifecycle = Any()
    private var job: Job? = null
    private val socket = AtomicReference<HaSocket?>()
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    private val _link = MutableStateFlow(Link.OFFLINE)
    val link = _link.asStateFlow()
    private val _entities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    val entities = _entities.asStateFlow()
    private val panel = HaPanelRemote()
    val panelState = panel.state
    val panelCommands = panel.commands

    fun select(ids: Set<String>) {
        selected.value = ids.filterTo(linkedSetOf(), ::validEntity)
    }

    fun setPanelWebhook(webhookId: String?) = panel.setWebhook(webhookId)

    fun start() {
        synchronized(lifecycle) {
            if (job?.isActive == true) return
            val worker = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                reconnect(coroutineContext.job)
            }
            job = worker
            worker.start()
        }
    }

    fun stop() {
        val (worker, session) = synchronized(lifecycle) {
            val worker = job
            job = null
            val session = socket.getAndSet(null)
            _link.value = Link.OFFLINE
            panel.updateConnection(PanelRemoteState.UNAVAILABLE)
            worker to session
        }
        worker?.cancel()
        session?.close()
    }

    fun currentSocket(): HaSocket = socket.get() ?: throw HaProblem("Home Assistant is reconnecting")

    suspend fun request(type: String, data: JSONObject): JSONObject = currentSocket().request(type, data)

    private suspend fun reconnect(worker: Job) {
        var wait = FIRST_RETRY_DELAY_MS
        while (updateIfRunning(worker) {
                _link.value = Link.CONNECTING
                panel.updateConnection(PanelRemoteState.CONNECTING)
            }) {
            var opened: HaSocket? = null
            try {
                val session = HaSocket(HaHttp.webSockets, credentials)
                opened = session
                if (!session.authenticate()) {
                    updateIfRunning(worker) {
                        _link.value = Link.AUTH_REJECTED
                        panel.updateConnection(PanelRemoteState.UNAVAILABLE)
                    }
                    return
                }
                if (!updateIfRunning(worker) { socket.set(session) }) return
                serve(worker, session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!updateIfRunning(worker) {
                        if (_link.value == Link.ONLINE) wait = FIRST_RETRY_DELAY_MS
                        if (opened != null) socket.compareAndSet(opened, null)
                        _link.value = Link.OFFLINE
                        panel.updateConnection(PanelRemoteState.UNAVAILABLE)
                    }) return
            } finally {
                if (opened != null) {
                    socket.compareAndSet(opened, null)
                    opened.close()
                }
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }

    private class Subscription(
        val id: Int,
        val entities: Set<String>,
        val snapshot: CompletableDeferred<Unit>,
    )

    private suspend fun serve(worker: Job, session: HaSocket) = coroutineScope {
        var subscription: Subscription? = null
        val panelChannel = panel.Channel(session) { socket.get() === session }
        launch { panelChannel.subscribe() }
        launch {
            var previousId: Int? = null
            selected.collect { ids ->
                val replacingId = previousId
                if (ids.isEmpty()) {
                    updateIfRunning(worker) {
                        subscription = null
                        _entities.value = emptyMap()
                        _link.value = Link.ONLINE
                    }
                    previousId = null
                } else {
                    val snapshot = CompletableDeferred<Unit>()
                    session.request("subscribe_entities", JSONObject().put("entity_ids", JSONArray(ids))) { id ->
                        updateIfRunning(worker) { subscription = Subscription(id, ids, snapshot) }
                        previousId = id
                    }
                    withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { snapshot.await() }
                        ?: throw HaProblem("Home Assistant didn't send the selected entity states")
                }
                replacingId?.let {
                    session.request("unsubscribe_events", JSONObject().put("subscription", it))
                }
            }
        }
        session.pump { message ->
            val event = message.optJSONObject("event") ?: return@pump
            val id = message.optInt("id")
            var handled = false
            val running = updateIfRunning(worker) {
                val current = subscription
                if (current != null && current.id == id) {
                    _entities.value = reduceEntities(
                        _entities.value, event, current.entities, initial = !current.snapshot.isCompleted,
                    )
                    _link.value = Link.ONLINE
                    current.snapshot.complete(Unit)
                    handled = true
                }
            }
            if (running && !handled) panelChannel.dispatch(id, event)
        }
    }

    private inline fun updateIfRunning(worker: Job, update: () -> Unit): Boolean = synchronized(lifecycle) {
        if (job !== worker || !worker.isActive) return@synchronized false
        update()
        true
    }
}
