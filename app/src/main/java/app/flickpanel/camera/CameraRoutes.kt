package app.flickpanel.camera

import app.flickpanel.config.TileSpec
import app.flickpanel.ha.CameraCapabilities
import app.flickpanel.ha.Entity

internal data class CameraSources(val nativeCamera: String, val videoCamera: String, val go2rtcStream: String?)

internal fun cameraSources(id: String, tile: TileSpec?, liveEntities: Map<String, Entity>): CameraSources {
    val video = tile?.cameraStream?.takeIf { liveEntities[it]?.available == true } ?: id
    val go2rtc = tile?.intercomStream
    // A go2rtc alias belongs to the original camera even when another entity supplies video.
    return CameraSources(if (go2rtc == null) video else id, video, go2rtc)
}

internal enum class CameraRoute(val talk: Boolean) {
    HOME_ASSISTANT_TALK(true), GO2RTC(true), HOME_ASSISTANT(false),
}

internal fun cameraRoutes(capabilities: CameraCapabilities, hasGo2RtcStream: Boolean): List<CameraRoute> = buildList {
    if (capabilities.twoWayAudio) add(CameraRoute.HOME_ASSISTANT_TALK)
    if (hasGo2RtcStream) add(CameraRoute.GO2RTC)
    if (capabilities.webRtc) add(CameraRoute.HOME_ASSISTANT)
}
