package app.flickpanel.ha

import app.flickpanel.config.validEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class HaClient(
    private val credentials: HaCredentials,
    private val scope: CoroutineScope
) {
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val socket = AtomicReference<HaSocket?>()
    private val _link = MutableStateFlow(Link.OFFLINE)
    val link = _link.asStateFlow()
    private val _entities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    val entities = _entities.asStateFlow()
    private var job: Job? = null

    fun select(ids: Set<String>) {
        selected.value = ids.filter(::validEntity).toSet()
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var wait = 750L
            while (isActive) {
                _link.value = Link.CONNECTING
                val session = HaSocket(events, credentials)
                try {
                    if (!session.authenticate()) {
                        _link.value = Link.AUTH_REJECTED
                        break
                    }
                    wait = 750
                    socket.set(session)
                    serve(session)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _link.value = Link.OFFLINE
                } finally {
                    socket.compareAndSet(session, null)
                    session.close()
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(15_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket.getAndSet(null)?.close()
        _link.value = Link.OFFLINE
    }

    private data class Subscription(val id: Int, val selected: Set<String>, var first: Boolean = true)

    private suspend fun serve(session: HaSocket) = coroutineScope {
        val subscription = AtomicReference<Subscription?>()
        launch {
            selected.collect { ids ->
                val previous = subscription.get()
                if (ids.isEmpty()) {
                    subscription.set(null)
                    _entities.value = emptyMap()
                    _link.value = Link.ONLINE
                } else {
                    session.request("subscribe_entities", JSONObject().put("entity_ids", JSONArray(ids))) { id ->
                        subscription.set(Subscription(id, ids))
                    }
                }
                if (previous != null) {
                    session.request("unsubscribe_events", JSONObject().put("subscription", previous.id))
                }
            }
        }
        session.pump { message ->
            val current = subscription.get()
            val event = message.optJSONObject("event")
            if (current != null && event != null && current.id == message.optInt("id")) {
                _entities.value = reduceEntities(_entities.value, event, current.selected, current.first)
                current.first = false
                _link.value = Link.ONLINE
            }
        }
    }

    suspend fun catalogue(): Catalogue = coroutineScope {
        val stateResponse = async { request("get_states").optJSONArray("result") ?: JSONArray() }
        val entitiesById = async { registryForDisplay() }
        val devices = async { indexed(optionalList("config/device_registry/list"), "id") }
        val areas = async {
            val list = optionalList("config/area_registry/list")
            objects(list).mapNotNull { row ->
                val id = row.text("area_id") ?: return@mapNotNull null
                Area(id, row.text("name") ?: id)
            }.sortedBy { it.name.lowercase() }
        }
        val registry = entitiesById.await()
        val hardware = devices.await()
        val entries = objects(stateResponse.await()).mapNotNull { row ->
            val id = row.optString("entity_id").takeIf(::validEntity) ?: return@mapNotNull null
            val entity = Entity(id, row.optString("state"), row.optJSONObject("attributes") ?: JSONObject())
            if (entity.kinds().isEmpty()) return@mapNotNull null
            val metadata = registry[id]
            Entry(entity, metadata?.area ?: hardware[metadata?.device]?.text("area_id"),
                metadata?.diagnostic == true, metadata?.hidden != true)
        }.sortedBy { it.entity.name.lowercase() }
        Catalogue(entries, areas.await())
    }

    private data class RegistryEntry(val area: String?, val device: String?, val diagnostic: Boolean, val hidden: Boolean)

    private suspend fun registryForDisplay(): Map<String, RegistryEntry> {
        val compact = try {
            request("config/entity_registry/list_for_display").optJSONObject("result")?.optJSONArray("entities")
        } catch (_: HaProblem) { null }
        if (compact != null) return indexed(compact, "ei").mapValues { (_, row) ->
            RegistryEntry(row.text("ai"), row.text("di"), !row.isNull("ec"), row.optBoolean("hb"))
        }
        return indexed(optionalList("config/entity_registry/list"), "entity_id").mapValues { (_, row) ->
            RegistryEntry(row.text("area_id"), row.text("device_id"), row.text("entity_category") != null,
                row.text("hidden_by") != null || row.text("disabled_by") != null)
        }
    }

    suspend fun command(
        entity: String,
        domain: String,
        service: String,
        values: JSONObject = JSONObject()
    ) {
        require(validEntity(entity) && domain == entity.substringBefore('.'))
        if (_link.value != Link.ONLINE) throw HaProblem("Home Assistant is reconnecting")
        request("call_service", JSONObject().put("domain", domain).put("service", service)
            .put("target", JSONObject().put("entity_id", entity)).put("service_data", values))
    }

    suspend fun browseMusic(entity: String, folder: MediaChoice?): List<MediaChoice> {
        require(validEntity(entity) && entity.startsWith("media_player."))
        val data = JSONObject().put("entity_id", entity)
        if (folder != null) data.put("media_content_id", folder.id).put("media_content_type", folder.type)
        val response = request("media_player/browse_media", data)
        val entries = response.optJSONObject("result")?.optJSONArray("children") ?: JSONArray()
        return objects(entries).mapNotNull(MediaChoice::from)
    }

    suspend fun history(entity: String): List<GraphPoint> {
        val now = Instant.now()
        val response = request("history/history_during_period", JSONObject()
            .put("start_time", now.minusSeconds(6 * 3600).toString())
            .put("end_time", now.toString()).put("entity_ids", JSONArray(listOf(entity)))
            .put("minimal_response", true).put("no_attributes", true).put("significant_changes_only", false))
        val rows = response.optJSONObject("result")?.optJSONArray(entity) ?: return emptyList()
        val points = objects(rows).mapNotNull { row ->
            val value = (row.text("s") ?: row.text("state"))?.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return@mapNotNull null
            val timestamp = row.optDouble("lu", row.optDouble("lc", Double.NaN)).takeIf(Double::isFinite)?.let { (it * 1000).toLong() }
                ?: runCatching {
                    Instant.parse(row.text("last_updated") ?: row.text("last_changed")).toEpochMilli()
                }.getOrNull() ?: return@mapNotNull null
            GraphPoint(timestamp, value)
        }
        val stride = (points.size / 160).coerceAtLeast(1)
        return points.filterIndexed { index, _ -> index % stride == 0 || index == points.lastIndex }.sortedBy { it.time }
    }

    suspend fun snapshot(entity: String): ByteArray? {
        require(validEntity(entity) && entity.startsWith("camera."))
        val url = credentials.server.resolve("api/camera_proxy/$entity")!!.newBuilder()
            .addQueryParameter("width", "720").build()
        return get(url, credentials.token, ::imageBody)
    }

    suspend fun artwork(reference: String): ByteArray? {
        val server = credentials.server
        val url = resolveArtwork(server, reference) ?: return null
        val token = credentials.token.takeIf { server.sameOrigin(url) }
        return get(url, token, ::imageBody)
    }

    suspend fun photoAlbum(id: String?): PhotoAlbum = browsePhotoAlbum(id, ::request)

    suspend fun photo(id: String): ByteArray? {
        val path = resolvePhoto(id, ::request) ?: return null
        val server = credentials.server
        val url = server.resolve(path) ?: return null
        val pathPrefix = if (id.startsWith("media-source://synology_dsm/")) "synology_dsm/" else "media/"
        if (!server.sameOrigin(url) || (!url.encodedPath.startsWith(server.encodedPath + pathPrefix) &&
                !url.encodedPath.startsWith("/$pathPrefix"))) return null
        return get(url, credentials.token) { imageBody(it, 12 * 1024 * 1024L) }
    }

    suspend fun stream(entity: String): HttpUrl {
        require(validEntity(entity) && entity.startsWith("camera."))
        val response = request("camera/stream", JSONObject().put("entity_id", entity).put("format", "hls"))
        val path = response.optJSONObject("result")?.optString("url").orEmpty()
        if (!path.matches(Regex("/api/hls/[A-Za-z0-9_-]{8,160}/[A-Za-z0-9_-]+\\.m3u8"))) {
            throw HaProblem("This camera has no supported live stream")
        }
        return credentials.server.resolve(path.removePrefix("/"))!!
    }

    val videoClient: OkHttpClient by lazy { http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            if (!credentials.server.sameOrigin(chain.request().url)) throw IOException("Unexpected media server")
            chain.proceed(chain.request())
        }.build() }

    private suspend fun request(type: String, data: JSONObject = JSONObject()): JSONObject =
        (socket.get() ?: throw HaProblem("Home Assistant is reconnecting")).request(type, data)

    private suspend fun optionalList(type: String): JSONArray =
        try { request(type).optJSONArray("result") ?: JSONArray() }
        catch (_: HaProblem) { JSONArray() }

    companion object {
        private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).build()
        private val events = http.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS).build()

        suspend fun validate(credentials: HaCredentials) {
            get(credentials.server.resolve("api/")!!, credentials.token) {
                if (!it.isSuccessful) throw HaProblem(
                    if (it.code in listOf(401, 403)) "Home Assistant rejected the token" else "Couldn't open the Home Assistant API"
                )
                val body = it.body?.source() ?: throw HaProblem("Couldn't read the Home Assistant API")
                if (body.request(16_385) || JSONObject(body.readUtf8()).optString("message") != "API running.") {
                    throw HaProblem("This address didn't return the Home Assistant API")
                }
            }
        }

        private fun objects(list: JSONArray): List<JSONObject> =
            (0 until list.length()).mapNotNull(list::optJSONObject)

        private fun indexed(list: JSONArray, key: String): Map<String, JSONObject> =
            objects(list).mapNotNull { row -> row.text(key)?.let { it to row } }.toMap()

        private fun imageBody(response: Response): ByteArray? = imageBody(response, 3_000_000)

        private fun imageBody(response: Response, limit: Long): ByteArray? {
            val body = response.body ?: return null
            val contentType = body.contentType() ?: return null
            if (!response.isSuccessful || contentType.type != "image" || contentType.subtype == "svg+xml") return null
            val source = body.source()
            if (source.request(limit + 1)) return null
            return source.readByteArray()
        }

        private suspend fun <T> get(url: HttpUrl, token: String?, read: (Response) -> T): T =
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(url)
                if (token != null) request.header("Authorization", "Bearer $token")
                val call = http.newCall(request.build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(HaProblem("Home Assistant is unreachable"))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try { continuation.resume(response.use(read)) }
                        catch (error: Exception) { continuation.resumeWithException(error) }
                    }
                })
            }
    }
}

internal fun resolveArtwork(server: HttpUrl, reference: String): HttpUrl? {
    val url = server.resolve(reference) ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val apiPath = server.encodedPath + "api/"
    val homeAssistantImage = server.sameOrigin(url) &&
        (url.encodedPath.startsWith(apiPath) || url.encodedPath.startsWith("/api/"))
    val musicAssistantImage = url.host == server.host && url.encodedPath.startsWith("/imageproxy/")
    val publicImage = url.isHttps && url.port == 443 && !server.sameOrigin(url)
    return url.takeIf { homeAssistantImage || musicAssistantImage || publicImage }
}
