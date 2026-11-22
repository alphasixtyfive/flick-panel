package app.flickpanel.updates

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val releaseHosts = setOf("github.com", "release-assets.githubusercontent.com",
    "objects.githubusercontent.com", "github-releases.githubusercontent.com")

internal class PanelUpdateDownload(cacheDirectory: File) {
    private val folder = File(cacheDirectory, "panel-updates")
    private val client = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.MINUTES).build()

    suspend fun check(installed: String, abis: List<String>): PanelRelease? =
        request(latestPanelReleaseUrl.toHttpUrl(), "application/vnd.github+json") { response, _ ->
            requireSuccess(response)
            val source = response.body?.source() ?: throw UpdateProblem("GitHub returned an empty release.")
            if (source.request(256 * 1024 + 1L)) throw UpdateProblem("The release information is too large.")
            parsePanelRelease(JSONObject(source.readUtf8()), installed, abis)
        }

    suspend fun cached(release: PanelRelease): File? = withContext(Dispatchers.IO) {
        apkFile(release).takeIf { it.isFile && it.length() == release.size }
    }

    suspend fun download(release: PanelRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val part = File.createTempFile("panel-", ".part", folder)
        try {
            fetchApk(release, part, progress)
            val apk = apkFile(release)
            if (!part.renameTo(apk)) throw UpdateProblem("The update could not be saved.")
            folder.listFiles()?.filter { it != apk && it.extension == "apk" }?.forEach { it.delete() }
            apk
        } finally {
            withContext(NonCancellable) { part.delete() }
        }
    }

    private fun apkFile(release: PanelRelease) = File(folder, "${release.sha256}.apk")

    private suspend fun fetchApk(release: PanelRelease, file: File, progress: (Int) -> Unit) {
        var url = release.url.toHttpUrl()
        repeat(5) {
            if (!trustedAssetUrl(url)) throw UpdateProblem("GitHub provided an unexpected download address.")
            val location = request(url, "application/octet-stream") { response, active ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    response.header("Location") ?: throw UpdateProblem("GitHub returned an invalid redirect.")
                } else {
                    requireSuccess(response)
                    writeApk(response, file, release, active, progress)
                    null
                }
            } ?: return
            url = url.resolve(location) ?: throw UpdateProblem("GitHub returned an invalid redirect.")
        }
        throw UpdateProblem("GitHub returned too many download redirects.")
    }

    private suspend fun <T> request(url: HttpUrl, accept: String, read: (Response, () -> Boolean) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url).header("Accept", accept).header("User-Agent", "Flick-Panel").build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(UpdateProblem("Could not reach GitHub. Check the connection and try again."))
                }

                override fun onResponse(call: Call, response: Response) {
                    val value = try {
                        response.use {
                            if (!continuation.isActive) return
                            read(it) { continuation.isActive }
                        }
                    } catch (failure: Exception) {
                        continuation.resumeWithException(failure)
                        return
                    }
                    continuation.resume(value)
                }
            })
        }

    private fun requireSuccess(response: Response) {
        if (response.code == 200) return
        throw UpdateProblem(when (response.code) {
            403, 429 -> "GitHub is limiting requests. Try again later."
            404 -> "No published update was found."
            else -> "GitHub could not complete the request. Try again later."
        })
    }
}

internal fun trustedAssetUrl(url: HttpUrl): Boolean =
    url.isHttps && url.host in releaseHosts && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && url.fragment == null

@WorkerThread
private fun writeApk(response: Response, file: File, release: PanelRelease, active: () -> Boolean, progress: (Int) -> Unit) {
    val body = response.body ?: throw UpdateProblem("GitHub returned an empty download.")
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    var reported = -1
    file.outputStream().use { output ->
        body.byteStream().use { input ->
            while (active()) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > release.size) throw UpdateProblem("The download exceeded the release size.")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                val percent = (total * 100 / release.size).toInt()
                if (percent != reported) {
                    progress(percent)
                    reported = percent
                }
            }
        }
    }
    if (total != release.size || digest.hexDigest() != release.sha256) {
        throw UpdateProblem("The update could not be verified. Download it again.")
    }
}

internal fun MessageDigest.hexDigest(): String = digest().joinToString("") { "%02x".format(it) }
