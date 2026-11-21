package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaProblem
import app.flickpanel.ha.Link
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Runs one command per entity and lets a cover stop supersede its current command. */
internal class DeviceCommandQueue(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PanelUi>,
    private val client: () -> HaClient?,
    private val reportFailure: (Exception) -> Unit,
) {
    private val requests = mutableMapOf<String, Job>()

    fun send(
        entity: Entity, service: String, payload: JSONObject = JSONObject(), pending: String = "Sending…",
        matches: ((Entity) -> Boolean)? = null, interruptPending: Boolean = false,
    ) {
        val active = client() ?: return
        val id = entity.id
        val current = state.value
        if (current.link != Link.ONLINE || current.entities[id]?.available != true) return
        val previous = requests[id]
        if (previous != null && !interruptPending) return

        val request = scope.launch(start = CoroutineStart.LAZY) {
            try {
                active.command(id, entity.domain, service, payload)
                if (matches != null) {
                    val confirmed = withTimeoutOrNull(4_500) {
                        active.entities.first { it[id]?.let(matches) == true }
                    }
                    if (confirmed == null) throw HaProblem("Home Assistant hasn't confirmed the change")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requests[id] === coroutineContext[Job] && client() === active) reportFailure(failure)
            } finally {
                if (requests[id] === coroutineContext[Job]) {
                    requests.remove(id)
                    state.update { it.copy(pending = it.pending - id) }
                }
            }
        }
        requests[id] = request
        state.update { it.copy(pending = it.pending + (id to pending)) }
        previous?.cancel()
        request.start()
    }

    fun cancel() {
        val active = requests.values.toList()
        requests.clear()
        state.update { if (it.pending.isEmpty()) it else it.copy(pending = emptyMap()) }
        active.forEach { it.cancel() }
    }
}
