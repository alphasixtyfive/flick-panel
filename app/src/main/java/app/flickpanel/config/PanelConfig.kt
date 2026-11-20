package app.flickpanel.config

import org.json.JSONArray
import org.json.JSONObject

enum class PanelTheme(val label: String) {
    SLATE("Slate blue"), AMBER("Charcoal amber"), PORCELAIN("Porcelain"), SYSTEM("Android colours")
}

enum class TileKind(val label: String) {
    TOGGLE("Switch"), VALUE("Value"), SLIDER("Slider"), TREND("Small graph"),
    ACTION("Button"), CAMERA("Camera"), CLIMATE("Thermostat")
}

data class TileSpec(
    val entity: String,
    val kind: TileKind,
    val label: String? = null,
    val statusEntity: String? = null
)

data class PanelConfig(
    val favourites: List<TileSpec> = emptyList(),
    val theme: PanelTheme = PanelTheme.SLATE,
    val messageEntity: String? = null,
    val presenceEntity: String? = null,
    val idleSeconds: Int = 60,
    val wallMode: Boolean = true
)

internal object PanelConfigCodec {
    fun encode(config: PanelConfig): String = JSONObject()
        .put("version", 1)
        .put("tiles", JSONArray(config.favourites.map { tile ->
            JSONObject().put("entity", tile.entity).put("kind", tile.kind.name).put("label", tile.label)
                .put("status", tile.statusEntity)
        }))
        .put("theme", config.theme.name)
        .put("message", config.messageEntity)
        .put("presence", config.presenceEntity)
        .put("idle", config.idleSeconds)
        .put("wall", config.wallMode)
        .toString()

    fun decode(json: String): PanelConfig {
        val obj = JSONObject(json)
        require(obj.optInt("version") == 1) { "This layout version requires a newer Flick Panel" }
        val list = obj.optJSONArray("tiles") ?: JSONArray()
        val tiles = (0 until list.length()).mapNotNull { index ->
            val tile = list.optJSONObject(index) ?: return@mapNotNull null
            val entity = tile.optString("entity").takeIf(::validEntity) ?: return@mapNotNull null
            val kind = TileKind.entries.firstOrNull { it.name == tile.optString("kind") } ?: return@mapNotNull null
            TileSpec(entity, kind, tile.text("label")?.take(72), tile.text("status")?.takeIf {
                kind == TileKind.ACTION && validEntity(it) && it.startsWith("timer.")
            })
        }.distinctBy { it.entity }
        return PanelConfig(
            favourites = tiles.take(48),
            theme = PanelTheme.entries.firstOrNull { it.name == obj.optString("theme") } ?: PanelTheme.SLATE,
            messageEntity = obj.text("message")?.takeIf(::validEntity),
            presenceEntity = obj.text("presence")?.takeIf { validEntity(it) && it.startsWith("binary_sensor.") },
            idleSeconds = obj.optInt("idle", 60).coerceIn(15, 900),
            wallMode = obj.optBoolean("wall", true)
        )
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}

private val entityPattern = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
internal fun validEntity(entity: String): Boolean = entity.length <= 255 && entityPattern.matches(entity)
