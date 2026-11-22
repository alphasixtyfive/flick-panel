package app.flickpanel

import app.flickpanel.updates.UpdateProblem
import app.flickpanel.updates.parsePanelRelease
import app.flickpanel.updates.trustedAssetUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelReleaseTest {
    private val version = "0.1.100"
    private val tag = "v$version"
    private val downloadPath = "https://github.com/alphasixtyfive/flick-panel/releases/download/$tag"

    @Test fun prefersDeviceAbiAndFallsBackToUniversal() {
        val release = release(asset("universal"), asset("armeabi-v7a"), asset("arm64-v8a"))
        val native = parsePanelRelease(release, "0.1.99", listOf("arm64-v8a", "armeabi-v7a"))
        val universal = parsePanelRelease(release, "0.1.99", listOf("x86_64", "x86"))

        assertEquals("$downloadPath/Flick-Panel-$version-arm64-v8a.apk", native?.url)
        assertEquals("$downloadPath/Flick-Panel-$version-universal.apk", universal?.url)
    }

    @Test fun neitherCurrentNorOlderReleasesAreOffered() {
        assertNull(parsePanelRelease(release(), version, listOf("arm64-v8a")))
        assertNull(parsePanelRelease(release(), "0.2.0", listOf("arm64-v8a")))
    }

    @Test fun rejectsPrereleasesForeignAssetsMissingDigestsAndDuplicateAssets() {
        val abi = "arm64-v8a"
        val invalid = listOf(
            release(asset(abi)).put("prerelease", true),
            release(asset(abi).put("browser_download_url", "https://example.test/update.apk")),
            release(asset(abi).put("digest", "")),
            release(asset(abi), asset(abi)),
        )
        for (candidate in invalid) {
            assertThrows(UpdateProblem::class.java) { parsePanelRelease(candidate, "0.1.99", listOf(abi)) }
        }
    }

    @Test fun redirectsStayOnTrustedHttpsHosts() {
        assertTrue(trustedAssetUrl("https://release-assets.githubusercontent.com/github-production-release-asset/123".toHttpUrl()))
        assertFalse(trustedAssetUrl("https://github.com.example.test/asset".toHttpUrl()))
        assertFalse(trustedAssetUrl("https://github.com:8080/asset".toHttpUrl()))
        assertFalse(trustedAssetUrl("http://github.com/asset".toHttpUrl()))
    }

    private fun release(vararg assets: JSONObject) = JSONObject()
        .put("draft", false).put("prerelease", false).put("tag_name", tag).put("assets", JSONArray(assets.toList()))

    private fun asset(abi: String): JSONObject {
        val name = "Flick-Panel-$version-$abi.apk"
        return JSONObject().put("name", name).put("state", "uploaded").put("size", 4096)
            .put("digest", "sha256:${"a".repeat(64)}").put("browser_download_url", "$downloadPath/$name")
    }
}
