package app.flickpanel.ha

import app.flickpanel.config.validEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

internal data class PanelRegistration(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
) {
    fun requestBody(): JSONObject {
        val name = deviceName.trim()
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl)) {
            "Enter a panel name of up to 80 characters"
        }
        require(deviceId.matches(Regex("[A-Za-z0-9_-]{8,128}")))
        return JSONObject()
            .put("app_id", "app.flickpanel")
            .put("app_name", "Flick Panel")
            .put("app_version", appVersion)
            .put("device_id", deviceId)
            .put("device_name", name)
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("os_name", "Android")
            .put("os_version", osVersion)
            .put("supports_encryption", false)
            .put("app_data", JSONObject().put("push_websocket_channel", true))
    }
}

internal enum class PanelRemoteState { DISABLED, CONNECTING, READY, UNAVAILABLE }

internal sealed interface PanelCommand {
    data object Wake : PanelCommand
    data object Sleep : PanelCommand
    data class OpenCamera(val entityId: String) : PanelCommand
}

internal fun validPanelWebhook(value: String): Boolean = value.length in 8..128 && value.all {
    it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
}

internal fun parsePanelCommand(event: JSONObject): PanelCommand? {
    if (event.opt("message") != "command_flick_panel") return null
    val data = event.optJSONObject("data") ?: return null
    return when (data.opt("command")) {
        "wake" -> PanelCommand.Wake
        "sleep" -> PanelCommand.Sleep
        "open_camera" -> (data.opt("camera") as? String)
            ?.takeIf { it.startsWith("camera.") && validEntity(it) }
            ?.let { PanelCommand.OpenCamera(it) }
        else -> null
    }
}

internal class HaPanelRemote {
    private class Binding(val webhookId: String)
    private data class Subscription(val id: Int, val binding: Binding)

    private val bindings = MutableStateFlow<Binding?>(null)
    private val _state = MutableStateFlow(PanelRemoteState.DISABLED)
    val state = _state.asStateFlow()
    private val _commands = MutableSharedFlow<PanelCommand>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    fun setWebhook(webhookId: String?) {
        val valid = webhookId?.takeIf(::validPanelWebhook)
        synchronized(this) {
            if (bindings.value?.webhookId == valid) return
            bindings.value = valid?.let(::Binding)
            updateConnection(PanelRemoteState.CONNECTING)
        }
    }

    fun updateConnection(status: PanelRemoteState) {
        synchronized(this) {
            _state.value = if (bindings.value == null) PanelRemoteState.DISABLED else status
        }
    }

    inner class Channel(private val socket: HaSocket, private val isCurrent: () -> Boolean) {
        private val subscription = AtomicReference<Subscription?>()

        suspend fun subscribe() {
            bindings.collectLatest { binding ->
                subscription.getAndSet(null)?.let { previous ->
                    try {
                        socket.request("unsubscribe_events", JSONObject().put("subscription", previous.id))
                    } catch (_: HaProblem) {
                        // A failed subscription may already be gone.
                    }
                }
                if (binding == null || !isCurrent()) return@collectLatest
                mark(binding, PanelRemoteState.CONNECTING)
                // HA may finish setting up a mobile app entry after returning its registration.
                for (pause in listOf(0L, 300L, 750L, 1_500L, 3_000L)) {
                    delay(pause)
                    try {
                        socket.request("mobile_app/push_notification_channel", JSONObject()
                            .put("webhook_id", binding.webhookId).put("support_confirm", false)) { id ->
                            subscription.set(Subscription(id, binding))
                        }
                        mark(binding, PanelRemoteState.READY)
                        return@collectLatest
                    } catch (_: HaProblem) {
                        subscription.set(null)
                    }
                }
                mark(binding, PanelRemoteState.UNAVAILABLE)
            }
        }

        fun dispatch(id: Int, event: JSONObject) {
            synchronized(this@HaPanelRemote) {
                val active = subscription.get() ?: return
                if (active.id != id || active.binding !== bindings.value || !isCurrent()) return
                parsePanelCommand(event)?.let { _commands.tryEmit(it) }
            }
        }

        private fun mark(binding: Binding, status: PanelRemoteState) {
            synchronized(this@HaPanelRemote) {
                if (bindings.value === binding && isCurrent()) _state.value = status
            }
        }
    }
}
