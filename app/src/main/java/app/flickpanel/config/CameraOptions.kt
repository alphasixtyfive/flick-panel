package app.flickpanel.config

enum class CameraActionIcon(val label: String) {
    AUTOMATIC("Automatic"),
    TEXT_ONLY("Text only"),
    LIGHT("Light"),
    GATE("Gate"),
    POWER("Power"),
    SOUND("Sound"),
    BELL("Bell"),
    PLAY("Play"),
}

data class CameraAction(
    val entity: String,
    val label: String? = null,
    val icon: CameraActionIcon = CameraActionIcon.AUTOMATIC,
)

internal const val cameraActionLimit = 3
internal const val cameraActionLabelLimit = 32
internal const val intercomStreamLimit = 80

private val intercomAlias = Regex("[A-Za-z0-9_][A-Za-z0-9_. -]{0,79}")
internal enum class CameraActionKind { TOGGLE, RUN, OPEN_GATE }

internal fun validIntercomStream(value: String) =
    value.length <= intercomStreamLimit && value == value.trim() && intercomAlias.matches(value)

internal fun cameraActionKind(entity: String): CameraActionKind? {
    if (!validEntity(entity)) return null
    return when (entity.substringBefore('.')) {
        "light", "switch", "input_boolean" -> CameraActionKind.TOGGLE
        "button", "input_button", "scene", "script" -> CameraActionKind.RUN
        "cover" -> CameraActionKind.OPEN_GATE
        else -> null
    }
}

internal fun validCameraAction(entity: String) = cameraActionKind(entity) != null

internal fun defaultCameraActionLabel(entity: String, name: String): String =
    if (cameraActionKind(entity) == CameraActionKind.OPEN_GATE) "Open gate" else name
