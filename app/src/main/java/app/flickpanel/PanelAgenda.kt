package app.flickpanel

import android.os.SystemClock
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.AgendaEvent
import app.flickpanel.ha.AgendaTask
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.ZoneId

private val taskRefreshDelays = listOf(0L, 150L, 300L, 600L, 1_200L)

internal sealed interface AgendaContent {
    data class Tasks(val items: List<AgendaTask>) : AgendaContent {
        private val groups by lazy { items.partition { !it.completed } }
        val open get() = groups.first
        val completed get() = groups.second
    }
    data class Calendar(val events: List<AgendaEvent>) : AgendaContent
}

internal data class AgendaState(
    val content: AgendaContent? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val issue: String? = null,
    val savedRevision: Long = 0L,
)

internal class PanelAgenda(private val scope: CoroutineScope) {
    private val statesById = MutableStateFlow<Map<String, AgendaState>>(emptyMap())
    val states = statesById.asStateFlow()
    private val requests = mutableMapOf<String, Job>()
    private val requestedAt = mutableMapOf<String, Long>()
    private val reads = Semaphore(3)

    fun cached(id: String): AgendaState? = statesById.value[id]

    fun refresh(client: HaClient, tile: TileSpec, force: Boolean) {
        val id = tile.entity
        val now = SystemClock.elapsedRealtime()
        if (requests[id]?.isActive == true || !force && requestedAt[id]?.let { now - it < 45_000 } == true) return
        requestedAt[id] = now
        update(id) { it.copy(loading = true, issue = null) }
        run(id) {
            val content = reads.withPermit {
                when (tile.kind) {
                    TileKind.TASKS -> AgendaContent.Tasks(client.tasks(id))
                    TileKind.CALENDAR -> {
                        val start = Instant.now()
                        val zone = ZoneId.systemDefault()
                        val end = start.atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant()
                        AgendaContent.Calendar(client.calendar(id, start, end, zone))
                    }
                    else -> return@run
                }
            }
            update(id) { it.copy(content = content) }
        }
    }

    fun changeTasks(
        client: HaClient, id: String, optimistic: AgendaContent.Tasks? = null,
        confirmed: (List<AgendaTask>) -> Boolean, change: suspend HaClient.() -> Unit,
    ): Boolean {
        if (requests[id]?.isActive == true) return false
        val previous = cached(id)?.content
        requestedAt[id] = SystemClock.elapsedRealtime()
        update(id) { it.copy(content = optimistic ?: previous, saving = true, issue = null) }
        run(id) {
            try {
                client.change()
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                requestedAt.remove(id)
                update(id) { it.copy(content = previous) }
                throw failure
            }
            update(id) { it.copy(savedRevision = it.savedRevision + 1) }
            try {
                // Some Home Assistant providers publish the new list after acknowledging the write.
                for (wait in taskRefreshDelays) {
                    delay(wait)
                    val tasks = client.tasks(id)
                    if (confirmed(tasks)) {
                        update(id) { it.copy(content = AgendaContent.Tasks(tasks)) }
                        return@run
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
            requestedAt.remove(id)
            update(id) { it.copy(issue = "Change saved. Refresh to update the list.") }
        }
        return true
    }

    fun keep(ids: Set<String>) {
        requests.keys.filter { it !in ids }.forEach { requests.remove(it)?.cancel() }
        requestedAt.keys.retainAll(ids)
        statesById.update { it.filterKeys(ids::contains) }
    }

    fun pause() {
        val reading = states.value.filterValues { it.loading }.keys
        reading.forEach { requests.remove(it)?.cancel() }
        requestedAt.clear()
        statesById.update { states -> states.mapValues { (_, state) -> state.copy(loading = false) } }
    }

    private fun run(id: String, operation: suspend () -> Unit) {
        val request = scope.launch(start = CoroutineStart.LAZY) {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                val message = (failure as? HaProblem)?.message ?: "Couldn't load this list"
                update(id) { it.copy(issue = message) }
            } finally {
                // A cancelled request may finish after the tile or connection has changed.
                if (requests[id] === coroutineContext[Job]) {
                    requests.remove(id)
                    update(id) { it.copy(loading = false, saving = false) }
                }
            }
        }
        requests[id] = request
        request.start()
    }

    private fun update(id: String, change: (AgendaState) -> AgendaState) {
        statesById.update { it + (id to change(it[id] ?: AgendaState())) }
    }
}
