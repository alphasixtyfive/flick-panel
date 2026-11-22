package app.flickpanel.updates

import org.json.JSONObject
import java.io.IOException

private const val repository = "alphasixtyfive/flick-panel"
internal const val latestPanelReleaseUrl = "https://api.github.com/repositories/1467091223/releases/latest"
private const val maxApkBytes = 128L * 1024 * 1024
private val versionPattern = Regex("v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")
private val digestPattern = Regex("sha256:([0-9a-fA-F]{64})")
private val panelAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

internal data class PanelRelease(val version: String, val url: String, val size: Long, val sha256: String)

internal fun parsePanelRelease(json: JSONObject, installed: String, supportedAbis: List<String>): PanelRelease? {
    if (json.opt("draft") != false || json.opt("prerelease") != false) {
        throw UpdateProblem("GitHub did not return a published release.")
    }
    val tag = json.getString("tag_name")
    val offered = versionParts(tag) ?: throw UpdateProblem("GitHub returned an unsupported release version.")
    val current = versionParts(installed) ?: throw UpdateProblem("This build has an unsupported version.")
    val change = offered.zip(current).firstOrNull { (left, right) -> left != right }
    if (change == null || change.first < change.second) return null

    val version = tag.removePrefix("v")
    val list = json.getJSONArray("assets")
    val assets = (0 until list.length()).mapNotNull(list::optJSONObject)
    for (abi in supportedAbis.filter { it in panelAbis } + "universal") {
        val name = "Flick-Panel-$version-$abi.apk"
        val matches = assets.filter { it.optString("name") == name }
        if (matches.isEmpty()) continue
        val asset = matches.singleOrNull() ?: throw UpdateProblem("The release has duplicate APKs.")
        val digest = digestPattern.matchEntire(asset.optString("digest"))?.groupValues?.get(1)?.lowercase()
        val size = asset.opt("size") as? Number
        val url = "https://github.com/$repository/releases/download/$tag/$name"
        if (asset.optString("state") != "uploaded" || asset.optString("browser_download_url") != url ||
            digest == null || size == null || size.toDouble() != size.toLong().toDouble() ||
            size.toLong() !in 1..maxApkBytes
        ) {
            throw UpdateProblem("GitHub returned an invalid APK for this release.")
        }
        return PanelRelease(version, url, size.toLong(), digest)
    }
    throw UpdateProblem("This release has no APK for this tablet.")
}

private fun versionParts(version: String): List<Int>? =
    versionPattern.matchEntire(version)?.groupValues?.drop(1)?.map(String::toInt)

internal class UpdateProblem(message: String) : IOException(message)
