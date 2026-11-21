package app.flickpanel.ha

import app.flickpanel.config.TileKind
import app.flickpanel.config.validEntity
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt
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
    val mediaFeatures get() = attributes.optLong("supported_features")
    val volumePercent get() = attributes.optDouble("volume_level", Double.NaN)
        .takeIf { it in 0.0..1.0 }?.let { (it * 100).roundToInt() }

    val isGate get() = domain == "cover" && attributes.text("device_class") == "gate"
    val gateWithOpenControl get() = isGate && supportsCoverAction(CoverAction.OPEN)
    val brightness get() = if (state == "off") 0 else
        attributes.optInt("brightness", -1).takeIf { it in 0..255 }?.let { (it * 100 + 127) / 255 }

    val options: List<String>
        get() {
            if (domain != "input_select" && domain != "select") return emptyList()
            val list = attributes.optJSONArray("options") ?: return emptyList()
            return (0 until minOf(list.length(), 256)).mapNotNull { index ->
                (list.opt(index) as? String)?.takeIf { it.isNotBlank() && it.length <= 255 }
            }.distinct()
        }

    val numberValue get() = state.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)

    val numberRange: NumberRange?
        get() {
            if (domain != "input_number" && domain != "number") return null
            val min = attributes.optDouble("min")
            val max = attributes.optDouble("max")
            val step = attributes.optDouble("step")
            if (!min.isFinite() || !max.isFinite() || !step.isFinite() || min >= max || step <= 0) return null
            return NumberRange(BigDecimal.valueOf(min), BigDecimal.valueOf(max), BigDecimal.valueOf(step))
        }

    fun kinds(): List<TileKind> = when (domain) {
        "light" -> {
            val modes = attributes.optJSONArray("supported_color_modes")
            val dimmable = brightness != null && state != "off" ||
                (0 until (modes?.length() ?: 0)).any { modes?.optString(it) !in setOf("", "onoff", "unknown") }
            if (dimmable) listOf(TileKind.SLIDER, TileKind.TOGGLE) else listOf(TileKind.TOGGLE)
        }
        "switch", "input_boolean" -> listOf(TileKind.TOGGLE)
        "sensor" -> if (state.toDoubleOrNull()?.isFinite() == true) {
            if (!hasReaderAttribute) listOf(TileKind.VALUE, TileKind.TREND)
            else listOf(TileKind.VALUE, TileKind.TREND, TileKind.READER)
        } else listOf(TileKind.VALUE, TileKind.READER)
        "input_number", "number" -> if (numberRange != null) listOf(TileKind.NUMBER, TileKind.VALUE) else listOf(TileKind.VALUE)
        "input_select", "select" -> if (options.isNotEmpty()) listOf(TileKind.CHOICE, TileKind.VALUE) else listOf(TileKind.VALUE)
        "timer" -> listOf(TileKind.TIMER, TileKind.VALUE)
        "todo" -> listOf(TileKind.TASKS)
        "calendar" -> listOf(TileKind.CALENDAR)
        "cover" -> if (CoverAction.entries.any(::supportsCoverAction)) listOf(TileKind.COVER, TileKind.VALUE)
            else listOf(TileKind.VALUE)
        "binary_sensor" -> listOf(TileKind.VALUE)
        "input_text" -> if (supportsReader) listOf(TileKind.VALUE, TileKind.READER) else listOf(TileKind.VALUE)
        "person" -> listOf(TileKind.PERSON)
        "input_button", "button", "scene", "script" -> listOf(TileKind.ACTION)
        "camera" -> listOf(TileKind.CAMERA)
        "image" -> listOf(TileKind.IMAGE)
        "climate" -> listOf(TileKind.CLIMATE)
        "media_player" -> if (mediaFeatures.hasMediaFeature(MediaTransport.PLAY.bit) || mediaFeatures.canBrowseMusic())
            listOf(TileKind.MEDIA) else emptyList()
        "weather" -> listOf(TileKind.WEATHER)
        else -> emptyList()
    }
}

internal enum class CoverAction(val feature: Long, val service: String, val label: String, val progress: String) {
    OPEN(1L, "open_cover", "Open", "Opening…"),
    CLOSE(2L, "close_cover", "Close", "Closing…"),
    STOP(8L, "stop_cover", "Stop", "Stopping…"),
}

internal fun Entity.supportsCoverAction(action: CoverAction) =
    domain == "cover" && attributes.optLong("supported_features") and action.feature != 0L

internal fun Entity.coverPosition(): Int? {
    if (domain != "cover" || !available) return null
    val position = (attributes.opt("current_position") as? Number)?.toDouble() ?: return null
    return position.takeIf { it in 0.0..100.0 && it == it.toInt().toDouble() }?.toInt()
}

internal fun Entity.canMoveCover(action: CoverAction, pending: String? = null): Boolean {
    if (!supportsCoverAction(action) || state !in setOf("open", "closed", "opening", "closing")) return false
    return when (action) {
        CoverAction.OPEN -> pending == null &&
            (state == "closed" || state == "open" && coverPosition()?.let { it in 1..99 } == true)
        CoverAction.CLOSE -> pending == null && state == "open"
        CoverAction.STOP -> pending != CoverAction.STOP.progress &&
            (pending != null || state == "opening" || state == "closing")
    }
}

internal fun Entity.gateToggleAction(pending: String? = null): CoverAction? {
    if (!isGate) return null
    val action = when (state) {
        "closed" -> CoverAction.OPEN
        "open" -> CoverAction.CLOSE
        else -> return null
    }
    return action.takeIf { canMoveCover(it, pending) }
}

internal val Entity.targetTemperature: Double?
    get() = attributes.optDouble("temperature").takeIf(Double::isFinite)

internal fun Entity.adjacentTemperature(increase: Boolean): Double? {
    if (domain != "climate" || !available) return null
    val current = targetTemperature ?: return null
    val minimum = attributes.optDouble("min_temp", 5.0)
    val maximum = attributes.optDouble("max_temp", 35.0)
    val step = attributes.optDouble("target_temp_step", .5).takeIf { it.isFinite() && it > 0 } ?: .5
    val next = current + if (increase) step else -step
    return next.takeIf { it != current && it in minimum..maximum }
}

internal data class NumberRange(val min: BigDecimal, val max: BigDecimal, val step: BigDecimal) {
    fun adjacent(value: BigDecimal, increase: Boolean): BigDecimal? {
        val rounding = if (increase) RoundingMode.FLOOR else RoundingMode.CEILING
        val index = value.subtract(min).divide(step, 0, rounding)
        val offset = if (increase) BigDecimal.ONE else BigDecimal.ONE.negate()
        return min.add(step.multiply(index.add(offset))).takeIf { it in min..max }?.stripTrailingZeros()
    }
}

internal fun Entity.binaryStatus(): String? {
    if (domain != "binary_sensor" || state !in setOf("on", "off")) return null
    val labels = when (attributes.text("device_class")) {
        "door", "window", "garage_door", "opening" -> "Open" to "Closed"
        "motion", "occupancy", "presence" -> "Detected" to "Clear"
        "moisture" -> "Wet" to "Dry"
        "battery" -> "Battery low" to "Battery OK"
        "plug" -> "Plugged in" to "Unplugged"
        "connectivity" -> "Connected" to "Disconnected"
        "power" -> "Powered" to "No power"
        "smoke" -> "Smoke detected" to "Clear"
        "gas" -> "Gas detected" to "Clear"
        "carbon_monoxide" -> "CO detected" to "Clear"
        "problem" -> "Problem" to "Clear"
        "running" -> "Running" to "Stopped"
        else -> return null
    }
    return if (state == "on") labels.first else labels.second
}

internal enum class MediaTransport(val bit: Long, val service: String) {
    PAUSE(1, "media_pause"), PLAY(16_384, "media_play")
}

internal const val MEDIA_VOLUME_SET = 4L

internal fun Long.hasMediaFeature(bit: Long) = this and bit != 0L
internal fun Long.canBrowseMusic() = hasMediaFeature(131_072L) && hasMediaFeature(512L)

internal data class MediaChoice(
    val id: String, val type: String, val title: String, val expandable: Boolean, val playable: Boolean,
    val artwork: String? = null
) {
    companion object {
        fun from(row: JSONObject): MediaChoice? {
            val id = row.text("media_content_id")?.takeIf { it.length <= 2048 } ?: return null
            val type = row.text("media_content_type")?.take(100) ?: return null
            val title = row.text("title")?.take(140) ?: return null
            val group = row.optString("media_class")
            val isAudio = group in setOf("music", "artist", "album", "track", "playlist", "genre", "podcast", "audiobook", "episode", "channel")
            val folder = row.optBoolean("can_expand") && (isAudio || group == "directory" || id.startsWith("media-source://radio_browser"))
            val playable = row.optBoolean("can_play") && (isAudio || type.startsWith("audio/"))
            val artwork = row.text("thumbnail")?.takeIf { it.isNotBlank() && it.length <= 4096 }
            return if (folder || playable) MediaChoice(id, type, title, folder, playable, artwork) else null
        }
    }
}

internal data class WeatherReading(val condition: String, val temperature: Double?, val unit: String)

internal fun Entity.weather(): WeatherReading? {
    if (domain != "weather" || !available) return null
    val condition = when (state) {
        "clear-night" -> "Clear night"
        "partlycloudy" -> "Partly cloudy"
        "lightning-rainy" -> "Thundery rain"
        "lightning" -> "Thunderstorms"
        "pouring" -> "Heavy rain"
        "rainy" -> "Rain"
        "snowy" -> "Snow"
        "snowy-rainy" -> "Sleet"
        "windy-variant" -> "Windy and cloudy"
        else -> state.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    return WeatherReading(
        condition,
        attributes.optDouble("temperature", Double.NaN).takeIf(Double::isFinite),
        attributes.text("temperature_unit").orEmpty().take(5)
    )
}

internal data class Area(val id: String, val name: String)
internal data class Entry(val entity: Entity, val area: String?, val diagnostic: Boolean, val visible: Boolean)
internal data class Catalogue(val entries: List<Entry> = emptyList(), val areas: List<Area> = emptyList()) {
    private val byId = entries.associateBy { it.entity.id }
    private val byRoom = entries.asSequence()
        .filter { it.area != null && it.visible && !it.diagnostic && it.entity.domain in setOf("light", "climate") }
        .sortedBy { it.entity.domain != "light" }
        .groupBy { it.area }
    val rooms = areas.filter { it.id in byRoom }

    fun entity(id: String) = byId[id]?.entity
    fun areaOf(id: String) = byId[id]?.area
    fun room(area: String): List<Entry> = byRoom[area].orEmpty()
}

internal data class GraphPoint(val time: Long, val value: Double)

internal fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun reduceEntities(
    previous: Map<String, Entity>, event: JSONObject, selected: Set<String>, initial: Boolean
): Map<String, Entity> {
    val updated = mutableMapOf<String, Entity>()
    if (!initial) previous.forEach { (id, entity) -> if (id in selected) updated[id] = entity }
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
            JSONObject().apply {
                current.attributes.keys().forEach { key -> put(key, current.attributes.get(key)) }
                addedAttrs?.let { for (key in it.keys()) put(key, it.get(key)) }
                for (index in 0 until (removedAttrs?.length() ?: 0)) remove(removedAttrs?.optString(index))
            }
        } else current.attributes
        updated[id] = current.copy(state = positive.optString("s", current.state), attributes = attributes)
    }
    return updated
}
