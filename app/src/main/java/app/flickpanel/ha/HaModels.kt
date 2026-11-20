package app.flickpanel.ha

import app.flickpanel.config.TileKind
import app.flickpanel.config.validEntity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal class HaCredentials(val server: HttpUrl, val token: String) {
    override fun toString() = "HaCredentials(redacted)"
}

internal fun parseServer(raw: String): HttpUrl {
    val url = raw.trim().toHttpUrlOrNull()
        ?: throw HaProblem("Enter the full Home Assistant URL, starting with https:// or http://")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "Enter the server URL without credentials, a query or a fragment"
    }
    require(url.isHttps || localAddress(url.host)) { "Use HTTPS for addresses outside your local network" }
    return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
}

internal fun localAddress(host: String): Boolean {
    val parts = host.split('.').map { it.toIntOrNull() ?: -1 }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 10 || parts[0] == 127 ||
        (parts[0] == 192 && parts[1] == 168) ||
        (parts[0] == 172 && parts[1] in 16..31)
}

internal fun HttpUrl.sameOrigin(other: HttpUrl) =
    scheme == other.scheme && host == other.host && port == other.port &&
        other.username.isEmpty() && other.password.isEmpty()

internal enum class Link { OFFLINE, CONNECTING, ONLINE, AUTH_REJECTED }
internal class HaProblem(message: String) : Exception(message)

internal data class Entity(
    val id: String,
    val state: String,
    val attributes: JSONObject = JSONObject()
) {
    val domain get() = id.substringBefore('.')
    val name get() = attributes.text("friendly_name") ?: id.substringAfter('.').replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
    val available get() = state.isNotBlank() && state !in setOf("unavailable", "unknown")
    val unit get() = attributes.text("unit_of_measurement").orEmpty()
    val brightness get() = if (state == "off") 0 else
        attributes.optInt("brightness", -1).takeIf { it in 0..255 }?.let { (it * 100 + 127) / 255 }

    fun kinds(): List<TileKind> = when (domain) {
        "light" -> {
            val modes = attributes.optJSONArray("supported_color_modes")
            val dimmable = brightness != null && state != "off" ||
                (0 until (modes?.length() ?: 0)).any { modes?.optString(it) !in setOf("", "onoff", "unknown") }
            if (dimmable) listOf(TileKind.SLIDER, TileKind.TOGGLE) else listOf(TileKind.TOGGLE)
        }
        "switch", "input_boolean" -> listOf(TileKind.TOGGLE)
        "sensor", "input_number", "number" -> if (domain == "sensor" && state.toDoubleOrNull()?.isFinite() == true)
            listOf(TileKind.VALUE, TileKind.TREND) else listOf(TileKind.VALUE)
        "binary_sensor", "input_text", "timer" -> listOf(TileKind.VALUE)
        "input_button", "button", "scene", "script" -> listOf(TileKind.ACTION)
        "camera" -> listOf(TileKind.CAMERA)
        "climate" -> listOf(TileKind.CLIMATE)
        else -> emptyList()
    }
}

internal data class Area(val id: String, val name: String)
internal data class Entry(val entity: Entity, val area: String?, val diagnostic: Boolean, val visible: Boolean)
internal data class Catalogue(val entries: List<Entry> = emptyList(), val areas: List<Area> = emptyList()) {
    fun room(area: String): List<Entry> = entries.filter {
        it.area == area && it.visible && !it.diagnostic && it.entity.domain in setOf("light", "climate")
    }.sortedWith(compareBy({ it.entity.domain != "light" }, { it.entity.name.lowercase() }))
}

internal data class GraphPoint(val time: Long, val value: Double)

internal fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun reduceEntities(
    previous: Map<String, Entity>, event: JSONObject, selected: Set<String>, initial: Boolean
): Map<String, Entity> {
    val updated = previous.filterKeys { it in selected && !initial }.toMutableMap()
    val add = event.optJSONObject("a") ?: JSONObject()
    for (id in add.keys()) {
        if (id !in selected || !validEntity(id)) continue
        val record = add.optJSONObject(id) ?: continue
        updated[id] = Entity(id, record.optString("s"), record.optJSONObject("a") ?: JSONObject())
    }
    val removed = event.optJSONArray("r")
    for (index in 0 until (removed?.length() ?: 0)) updated.remove(removed?.optString(index))
    val changes = event.optJSONObject("c") ?: JSONObject()
    for (id in changes.keys()) {
        val current = updated[id] ?: continue
        val change = changes.optJSONObject(id) ?: continue
        val positive = change.optJSONObject("+") ?: JSONObject()
        val addedAttrs = positive.optJSONObject("a")
        val removedAttrs = change.optJSONObject("-")?.optJSONArray("a")
        val attributes = if (addedAttrs != null || removedAttrs != null) {
            JSONObject(current.attributes.toString()).apply {
                addedAttrs?.let { for (key in it.keys()) put(key, it.get(key)) }
                for (index in 0 until (removedAttrs?.length() ?: 0)) remove(removedAttrs?.optString(index))
            }
        } else current.attributes
        updated[id] = current.copy(state = positive.optString("s", current.state), attributes = attributes)
    }
    return updated
}
