package app.flickpanel.ha

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object HaHttp {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    val webSockets = client.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun videoClient(server: HttpUrl): OkHttpClient = client.newBuilder()
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            if (!server.sameOrigin(chain.request().url)) throw IOException("Unexpected media server")
            chain.proceed(chain.request())
        }
        .build()

    suspend fun <T> get(url: HttpUrl, token: String?, read: (Response) -> T): T =
        exchange(Request.Builder().url(url), token, read)

    suspend fun <T> exchange(request: Request.Builder, token: String?, read: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            if (token != null) request.header("Authorization", "Bearer $token")
            val call = client.newCall(request.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(HaProblem("Home Assistant is unreachable"))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response.use(read))
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
}

internal fun Response.readJson(limit: Long = 16_384): JSONObject? {
    val source = body?.source() ?: return null
    if (source.request(limit + 1)) return null
    return try {
        JSONObject(source.readUtf8())
    } catch (_: JSONException) {
        null
    }
}

internal fun Response.readImage(limit: Long = 3_000_000): ByteArray? {
    val body = body ?: return null
    val type = body.contentType() ?: return null
    if (!isSuccessful || type.type != "image" || type.subtype == "svg+xml") return null
    val source = body.source()
    return if (source.request(limit + 1)) null else source.readByteArray()
}
