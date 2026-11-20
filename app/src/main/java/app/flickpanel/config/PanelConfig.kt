package app.flickpanel.config

import app.flickpanel.ha.PhotoFolder
import app.flickpanel.ha.isPhotoSourceId
import org.json.JSONArray
import org.json.JSONObject

enum class PanelTheme(val label: String) {
    SLATE("Graphite"), AMBER("Charcoal amber"), PORCELAIN("Porcelain"), SYSTEM("Android colours")
}

enum class TileKind(val label: String) {
    TOGGLE("Switch"), VALUE("Value"), SLIDER("Slider"), TREND("Small graph"),
    ACTION("Button"), CAMERA("Camera"), CLIMATE("Thermostat"), WEATHER("Weather"), MEDIA("Music player"), APP("Android app"), IMAGE("Image");

    internal val resizable get() = this != CAMERA && this != WEATHER && this != APP
}

data class TileSpec(
    val entity: String,
    val kind: TileKind,
    val label: String? = null,
    val statusEntity: String? = null,
    val showOnHome: Boolean = true,
    val wideOnTablet: Boolean = false,
) {
    internal val appPackage get() = entity.removePrefix("app:").takeIf { kind == TileKind.APP && validAppTarget(entity) }
}

internal data class PanelConfig(
    val favourites: List<TileSpec> = emptyList(),
    val theme: PanelTheme = PanelTheme.SLATE,
    val messageEntity: String? = null,
    val presenceEntity: String? = null,
    val idleSeconds: Int = 60,
    val wallMode: Boolean = true,
    val roomIds: List<String>? = null,
    internal val idleAlbum: PhotoFolder? = null
)

internal object PanelConfigCodec {
    fun encode(config: PanelConfig): String = JSONObject()
        .put("version", 1)
        .put("tiles", JSONArray(config.favourites.map { tile ->
            JSONObject().put("entity", tile.entity).put("kind", tile.kind.name).put("label", tile.label)
                .put("status", tile.statusEntity).put("home", tile.showOnHome)
                .apply { if (tile.wideOnTablet) put("wide", true) }
        }))
        .put("theme", config.theme.name)
        .put("message", config.messageEntity)
        .put("presence", config.presenceEntity)
        .put("idle", config.idleSeconds)
        .put("wall", config.wallMode)
        .put("rooms", config.roomIds?.let(::JSONArray))
        .put("idleAlbum", config.idleAlbum?.let { JSONObject().put("id", it.id).put("title", it.title) })
        .toString()

    fun decode(json: String): PanelConfig {
        val obj = JSONObject(json)
        require(obj.optInt("version") == 1) { "This layout version requires a newer Flick Panel" }
        val list = obj.optJSONArray("tiles") ?: JSONArray()
        val tiles = (0 until list.length()).mapNotNull { index ->
            val tile = list.optJSONObject(index) ?: return@mapNotNull null
            val kind = TileKind.entries.firstOrNull { it.name == tile.optString("kind") } ?: return@mapNotNull null
            val entity = tile.optString("entity")
                .takeIf { if (kind == TileKind.APP) validAppTarget(it) else validEntity(it) } ?: return@mapNotNull null
            TileSpec(entity, kind, tile.text("label")?.take(72), tile.text("status")?.takeIf {
                kind == TileKind.ACTION && validEntity(it) && it.startsWith("timer.")
            }, showOnHome = kind != TileKind.CAMERA || tile.optBoolean("home", true),
                wideOnTablet = kind.resizable && tile.optBoolean("wide"))
        }.distinctBy { it.entity }
        return PanelConfig(
            favourites = tiles.take(48),
            theme = PanelTheme.entries.firstOrNull { it.name == obj.optString("theme") } ?: PanelTheme.SLATE,
            messageEntity = obj.text("message")?.takeIf(::validEntity),
            presenceEntity = obj.text("presence")?.takeIf { validEntity(it) && it.startsWith("binary_sensor.") },
            idleSeconds = obj.optInt("idle", 60).coerceIn(15, 900),
            wallMode = obj.optBoolean("wall", true),
            roomIds = obj.optJSONArray("rooms")?.let { areas ->
                (0 until areas.length()).mapNotNull { areas.optString(it).takeIf(::validAreaId) }.distinct().take(48)
            },
            idleAlbum = obj.optJSONObject("idleAlbum")?.let { album ->
                val id = album.text("id")?.takeIf(::isPhotoSourceId) ?: return@let null
                PhotoFolder(id, album.text("title")?.take(140) ?: "Photo folder")
            }
        )
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}

private val entityPattern = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
internal fun validEntity(entity: String): Boolean = entity.length <= 255 && entityPattern.matches(entity)

private val appPattern = Regex("app:[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private fun validAppTarget(target: String): Boolean = target.length <= 259 && appPattern.matches(target)
private val areaPattern = Regex("[A-Za-z0-9_-]{1,128}")
private fun validAreaId(areaId: String): Boolean = areaPattern.matches(areaId)
