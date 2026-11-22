package app.flickpanel

import app.flickpanel.camera.CameraRoute
import app.flickpanel.camera.CameraSources
import app.flickpanel.camera.cameraRoutes
import app.flickpanel.camera.cameraSources
import app.flickpanel.config.CameraAction
import app.flickpanel.config.CameraActionIcon
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.PanelConfigCodec
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.signedIntercomUrl
import app.flickpanel.ha.CameraCapabilities
import app.flickpanel.ha.Entity
import app.flickpanel.ha.decodeNativeIceCandidate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraIntercomTest {
    @Test
    fun previewAndViewerResolveTheSameLiveSource() {
        val camera = TileSpec("camera.door", TileKind.CAMERA, cameraStream = "camera.door_hd")
        val live = mapOf("camera.door_hd" to Entity("camera.door_hd", "idle"))
        assertEquals(CameraSources("camera.door_hd", "camera.door_hd", null), cameraSources(camera.entity, camera, live))
        val intercom = camera.copy(intercomStream = "door")
        assertEquals(CameraSources("camera.door", "camera.door_hd", "door"), cameraSources(camera.entity, intercom, live))
        assertEquals(CameraSources("camera.door", "camera.door", "door"), cameraSources(camera.entity, intercom, emptyMap()))
    }

    @Test
    fun advertisedTalkbackPrefersHomeAssistantThenConfiguredGo2Rtc() {
        val capabilities = CameraCapabilities.from(JSONObject(
            """{"frontend_stream_types":["web_rtc","hls"],"two_way_audio":true}""",
        ))
        assertEquals(
            listOf(CameraRoute.HOME_ASSISTANT_TALK, CameraRoute.GO2RTC, CameraRoute.HOME_ASSISTANT),
            cameraRoutes(capabilities, hasGo2RtcStream = true),
        )
        assertEquals(
            listOf(CameraRoute.HOME_ASSISTANT_TALK, CameraRoute.HOME_ASSISTANT),
            cameraRoutes(capabilities, hasGo2RtcStream = false),
        )
    }

    @Test
    fun receiveOnlyAndOlderCamerasUseTheirAvailableRoutes() {
        val capabilities = CameraCapabilities.from(JSONObject("""{"frontend_stream_types":["web_rtc","hls"]}"""))
        assertEquals(listOf(CameraRoute.GO2RTC, CameraRoute.HOME_ASSISTANT), cameraRoutes(capabilities, true))
        assertEquals(listOf(CameraRoute.HOME_ASSISTANT), cameraRoutes(capabilities, false))
        val hlsOnly = CameraCapabilities.from(JSONObject("""{"frontend_stream_types":["hls"],"two_way_audio":true}"""))
        assertEquals(emptyList<CameraRoute>(), cameraRoutes(hlsOnly, false))
        assertEquals(listOf(CameraRoute.GO2RTC), cameraRoutes(CameraCapabilities.None, true))
    }

    @Test
    fun nativeIceCandidateAcceptsAnIndexWithoutPassingNullToAndroid() {
        val sdp = "candidate:1 1 udp 1 192.0.2.10 5000 typ host"
        val indexOnly = decodeNativeIceCandidate(JSONObject().put("candidate", sdp).put("sdpMLineIndex", 1))
        assertEquals("", indexOnly?.sdpMid)
        assertEquals(1, indexOnly?.sdpMLineIndex)
        val midOnly = decodeNativeIceCandidate(JSONObject().put("candidate", sdp).put("sdpMid", "video"))
        assertEquals("video", midOnly?.sdpMid)
        assertNull(decodeNativeIceCandidate(JSONObject().put("candidate", sdp)))
        assertNull(decodeNativeIceCandidate(JSONObject().put("candidate", "").put("sdpMLineIndex", 0)))
    }

    @Test
    fun legacyGateMovesIntoCameraActions() {
        val legacy = """{"version":1,"tiles":[{"entity":"camera.gate","kind":"CAMERA","gate":"cover.front_gate"}]}"""
        val restored = PanelConfigCodec.decode(legacy)
        assertEquals(listOf(CameraAction("cover.front_gate")), restored.favourites.single().cameraActions)
        assertEquals(restored, PanelConfigCodec.decode(PanelConfigCodec.encode(restored)))
    }

    @Test
    fun cameraOptionsRoundTripInDisplayOrder() {
        val tile = TileSpec("camera.porch", TileKind.CAMERA, intercomStream = "front_door_low", cameraActions = listOf(
            CameraAction("light.porch", icon = CameraActionIcon.TEXT_ONLY),
            CameraAction("script.leave_package", "Leave package", CameraActionIcon.SOUND),
        ))
        val config = PanelConfig(favourites = listOf(tile))
        assertEquals(config, PanelConfigCodec.decode(PanelConfigCodec.encode(config)))
    }

    @Test
    fun restoringRejectsArbitraryCameraTargetsAndStreamUrls() {
        val layout = """{"version":1,"tiles":[{"entity":"camera.gate","kind":"CAMERA",
            "intercom":"http://example.test/camera",
            "cameraActions":[{"entity":"lock.front_door"},{"entity":"light.driveway"},{"entity":"light.driveway"}]}]}"""
        val restored = PanelConfigCodec.decode(layout).favourites.single()
        assertNull(restored.intercomStream)
        assertEquals(listOf(CameraAction("light.driveway")), restored.cameraActions)
    }

    @Test
    fun unknownActionIconKeepsTheActionWithItsAutomaticAppearance() {
        val data = """{"version":1,"tiles":[{"entity":"camera.porch","kind":"CAMERA",
            "cameraActions":[{"entity":"light.porch","icon":"FUTURE"}]}]}"""
        val action = PanelConfigCodec.decode(data).favourites.single().cameraActions.single()
        assertEquals(CameraAction("light.porch"), action)
    }

    @Test
    fun proxyUrlUsesOnlyTheSignedHomeAssistantPathAndAStreamName() {
        val server = "https://ha.example.test/panel/".toHttpUrl()
        val url = signedIntercomUrl(server, "/api/webrtc/ws?authSig=test&server=https://other.test", "Gate SD")
        assertEquals("https://ha.example.test/panel/api/webrtc/ws?authSig=test&url=Gate%20SD", url.toString())
        assertNull(signedIntercomUrl(server, "https://other.test/api/webrtc/ws?authSig=test", "gate"))
        assertNull(signedIntercomUrl(server, "/api/webrtc/ws?token=test", "gate"))
        assertNull(signedIntercomUrl(server, "/api/webrtc/ws?authSig=test", "rtsp://example.test/stream"))
    }
}
