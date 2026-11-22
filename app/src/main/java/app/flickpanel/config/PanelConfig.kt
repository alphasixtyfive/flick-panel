package app.flickpanel.config

import app.flickpanel.ha.PhotoFolder

enum class PanelTheme(val label: String) {
    SLATE("Graphite"), AMBER("Charcoal amber"), PORCELAIN("Porcelain"), SYSTEM("Android colours")
}

enum class TileAccent(val label: String) {
    BLUE("Blue"), TEAL("Teal"), GREEN("Green"), AMBER("Amber"), CORAL("Coral"), ROSE("Rose")
}

enum class TileKind(val label: String) {
    TOGGLE("Switch"), VALUE("Value"), SLIDER("Slider"), TREND("Small graph"),
    ACTION("Button"), CAMERA("Camera"), CLIMATE("Thermostat"), WEATHER("Weather"), MEDIA("Music player"),
    APP("Android app"), IMAGE("Image"), TIMER("Countdown"), CHOICE("Selector"), NUMBER("Number control"),
    TASKS("Tasks"), CALENDAR("Calendar"), COVER("Cover"), PERSON("Person location"), READER("Content reader"),
    FAN("Fan"), PERCENTAGE("Percentage"), VACUUM("Vacuum");

    internal val resizable get() = this != CAMERA && this != WEATHER && this != APP
    internal val isAgenda get() = this == TASKS || this == CALENDAR
    internal val hasContentScreen get() = this == PERSON || this == READER
    internal val supportsAccent get() = when (this) {
        CAMERA, WEATHER, APP, IMAGE, PERSON -> false
        else -> true
    }
    internal val supportsIcon get() = supportsAccent
}

data class TileSpec(
    val entity: String,
    val kind: TileKind,
    val label: String? = null,
    val statusEntity: String? = null,
    val showOnHome: Boolean = true,
    val wideOnTablet: Boolean = false,
    val confirmAction: Boolean = false,
    val readerAttribute: String? = null,
    val cameraStream: String? = null,
    val accent: TileAccent? = null,
    val intercomStream: String? = null,
    val cameraActions: List<CameraAction> = emptyList(),
    val vacuumMap: String? = null,
    val vacuumBattery: String? = null,
    val icon: TileIcon? = null,
) {
    internal val appPackage get() = entity.removePrefix("app:").takeIf { kind == TileKind.APP && validAppTarget(entity) }
}

internal data class RoomTilePreference(
    val entity: String,
    val lightFormat: TileKind? = null,
    val wideOnTablet: Boolean = false,
    val hidden: Boolean = false,
    val label: String? = null,
    val accent: TileAccent? = null,
    val followsArea: Boolean = false,
    val icon: TileIcon? = null,
)

internal const val defaultScreensaverTimeoutSeconds = 300
internal val screensaverTimeoutRange = 15..1_800

internal data class PanelConfig(
    val favourites: List<TileSpec> = emptyList(),
    val theme: PanelTheme = PanelTheme.SLATE,
    val messageEntity: String? = null,
    val screensaverMessageEntity: String? = null,
    val idleSeconds: Int = defaultScreensaverTimeoutSeconds,
    val wallMode: Boolean = true,
    val dashboardBrightness: Int? = null,
    val screensaverBrightness: Int? = null,
    val roomIds: List<String>? = null,
    val showRoomHeating: Boolean = true,
    val roomTiles: Map<String, List<RoomTilePreference>> = emptyMap(),
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    internal val idleAlbum: PhotoFolder? = null,
    val homeBadges: List<HomeBadge> = emptyList(),
) {
    val vacuumTile get() = favourites.firstOrNull { it.kind == TileKind.VACUUM }
    val defaultScreensaverBrightness get() = if (idleAlbum == null) 13 else 55
}

internal const val roomTileLimit = 32

private val entityPattern = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
internal fun validEntity(entity: String): Boolean = entity.length <= 255 && entityPattern.matches(entity)

internal fun isVacuumMap(id: String) = id.startsWith("image.") && validEntity(id)

internal fun isVacuumBattery(id: String) = id.startsWith("sensor.") && validEntity(id)

internal fun roomTileKind(entity: String): TileKind? = when (entity.substringBefore('.')) {
    "light", "switch", "input_boolean" -> TileKind.TOGGLE
    "fan" -> TileKind.FAN
    "cover" -> TileKind.COVER
    "select", "input_select" -> TileKind.CHOICE
    "number", "input_number" -> TileKind.NUMBER
    "climate" -> TileKind.CLIMATE
    "media_player" -> TileKind.MEDIA
    "scene" -> TileKind.ACTION
    else -> null
}

private val appPattern = Regex("app:[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
internal fun validAppTarget(target: String): Boolean = target.length <= 259 && appPattern.matches(target)
