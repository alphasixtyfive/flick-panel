package app.flickpanel.ha

import app.flickpanel.config.validEntity
import app.flickpanel.config.validIntercomStream
import app.flickpanel.camera.CameraAudioOffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val streamPath = Regex("/api/hls/[A-Za-z0-9_-]{8,160}/[A-Za-z0-9_-]+\\.m3u8")
private const val MAX_PHOTO_BYTES = 12 * 1024 * 1024L

internal class CameraStream(val url: HttpUrl, val transport: OkHttpClient)

internal class HaClient(
    private val credentials: HaCredentials,
    scope: CoroutineScope,
) {
    private val connection = HaConnection(credentials, scope)
    val connectionState = connection.state
    val panelRemoteState = connection.panelState
    val panelCommands = connection.panelCommands
    private val videoClient: OkHttpClient by lazy { HaHttp.videoClient(credentials.server) }
    private val cameraCapabilities = ConcurrentHashMap<String, Pair<Long, CameraCapabilities>>()

    fun start() = connection.start()
    fun retry() = connection.retry()
    fun stop() = connection.stop()
    fun select(ids: Set<String>) = connection.select(ids)
    fun setPanelWebhook(webhookId: String?) = connection.setPanelWebhook(webhookId)

    suspend fun catalogue(): Catalogue = loadCatalogue { request(it) }

    suspend fun publishPanelSensors(webhookId: String, readings: List<PanelSensorReading>): Set<PanelSensor> {
        if (readings.isEmpty()) return emptySet()
        val result = panelWebhook(webhookId, "update_sensor_states", JSONArray(readings.map(PanelSensorReading::update)))
        return buildSet {
            for (reading in readings) {
                val status = result.optJSONObject(reading.sensor.id)
                if (status?.optBoolean("success") == true) {
                    add(reading.sensor)
                } else if (status?.optJSONObject("error")?.optString("code") == "not_registered") {
                    try {
                        if (panelWebhook(webhookId, "register_sensor", reading.registration()).optBoolean("success")) {
                            add(reading.sensor)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the failed registration eligible for retry without blocking other sensors.
                    }
                }
            }
        }
    }

    private suspend fun panelWebhook(webhookId: String, type: String, data: Any): JSONObject {
        require(validPanelWebhook(webhookId))
        val url = checkNotNull(credentials.server.resolve("api/webhook/$webhookId"))
        val body = JSONObject().put("type", type).put("data", data).toString().toRequestBody(JSON_MEDIA_TYPE)
        return HaHttp.exchange(Request.Builder().url(url).post(body), null) { response ->
            response.takeIf { it.isSuccessful }?.readJson()
                ?: throw HaProblem("Home Assistant could not update the tablet sensors")
        }
    }

    suspend fun registerPanel(registration: PanelRegistration): String {
        val url = checkNotNull(credentials.server.resolve("api/mobile_app/registrations"))
        val body = registration.requestBody().toString().toRequestBody(JSON_MEDIA_TYPE)
        return HaHttp.exchange(Request.Builder().url(url).post(body), credentials.token) { response ->
            if (response.code != 201) throw HaProblem(when (response.code) {
                401, 403 -> "Home Assistant did not allow this panel to register"
                404 -> "Enable the Mobile App integration in Home Assistant to link this panel"
                else -> "Home Assistant could not register this panel"
            })
            val id = response.readJson()?.opt("webhook_id") as? String
            id?.takeIf(::validPanelWebhook) ?: throw HaProblem("Home Assistant returned an invalid panel link")
        }
    }

    suspend fun command(entity: String, domain: String, service: String, values: JSONObject = JSONObject()) {
        callEntityService(entity, domain, service, values)
    }

    private suspend fun callEntityService(
        entity: String,
        domain: String,
        service: String,
        values: JSONObject,
        returnResponse: Boolean = false,
    ): JSONObject {
        require(validEntity(entity) && domain == entity.substringBefore('.'))
        if (connectionState.value.link != Link.ONLINE) throw HaProblem("Home Assistant is reconnecting")
        val data = JSONObject().put("domain", domain).put("service", service)
            .put("target", JSONObject().put("entity_id", entity)).put("service_data", values)
        if (returnResponse) data.put("return_response", true)
        return request("call_service", data)
    }

    suspend fun tasks(entity: String): List<AgendaTask> {
        val data = JSONObject().put("status", JSONArray(listOf("needs_action", "completed")))
        val reply = callEntityService(entity, "todo", "get_items", data, returnResponse = true)
        return decodeAgendaTasks(reply.entityServiceRows(entity, "items"))
    }

    suspend fun calendar(
        entity: String, start: Instant, end: Instant, zone: ZoneId = ZoneId.systemDefault(),
    ): List<AgendaEvent> {
        require(start < end)
        val data = JSONObject().put("start_date_time", start.toString()).put("end_date_time", end.toString())
        val reply = callEntityService(entity, "calendar", "get_events", data, returnResponse = true)
        return decodeAgendaEvents(reply.entityServiceRows(entity, "events"), zone)
    }

    suspend fun setTaskCompleted(entity: String, uid: String, completed: Boolean) {
        require(validTaskUid(uid))
        val taskList = connectionState.value.entities[entity]
        if (taskList?.available != true || !taskList.canUpdateTasks) {
            throw HaProblem("This task list does not support completing tasks")
        }
        command(entity, "todo", "update_item", JSONObject().put("item", uid)
            .put("status", if (completed) "completed" else "needs_action"))
    }

    suspend fun addTask(entity: String, summary: String) {
        val title = normalizedTaskTitle(summary) ?: throw HaProblem("Enter a task name of up to $MAX_TASK_TITLE_LENGTH characters")
        val taskList = connectionState.value.entities[entity]
        if (taskList?.available != true || !taskList.canAddTasks) {
            throw HaProblem("This task list does not support adding tasks")
        }
        command(entity, "todo", "add_item", JSONObject().put("item", title))
    }

    suspend fun browseMusic(entity: String, folder: MediaChoice?): List<MediaChoice> {
        require(validEntity(entity) && entity.startsWith("media_player."))
        val data = JSONObject().put("entity_id", entity)
        if (folder != null) data.put("media_content_id", folder.id).put("media_content_type", folder.type)
        val rows = request("media_player/browse_media", data).optJSONObject("result")?.optJSONArray("children")
        return rows.objects().mapNotNull(MediaChoice::from).toList()
    }

    suspend fun vacuumRooms(entity: String): List<Area> {
        require(validEntity(entity) && entity.startsWith("vacuum."))
        val entry = request("config/entity_registry/get", JSONObject().put("entity_id", entity))
            .optJSONObject("result")
        val mapped = vacuumAreaIds(entry)
        if (mapped.isEmpty()) return emptyList()
        return decodeAreas(request("config/area_registry/list").optJSONArray("result")).filter { it.id in mapped }
    }

    suspend fun history(entity: String): GraphHistory {
        require(validEntity(entity))
        val now = Instant.now()
        val start = now.minusSeconds(6 * 3600)
        val response = request("history/history_during_period", JSONObject()
            .put("start_time", start.toString())
            .put("end_time", now.toString()).put("entity_ids", JSONArray(listOf(entity)))
            .put("minimal_response", true).put("no_attributes", true).put("significant_changes_only", false))
        return GraphHistory(start.toEpochMilli(), now.toEpochMilli(),
            decodeHistory(response.optJSONObject("result")?.optJSONArray(entity)))
    }

    suspend fun snapshot(entity: String, width: Int = 720): ByteArray? {
        require(validEntity(entity))
        val path = when {
            entity.startsWith("camera.") -> "api/camera_proxy/$entity?width=$width"
            entity.startsWith("image.") -> "api/image_proxy/$entity"
            else -> return null
        }
        val url = credentials.server.resolve(path) ?: return null
        return HaHttp.get(url, credentials.token) { it.readImage() }
    }

    suspend fun artwork(reference: String): ByteArray? {
        val server = credentials.server
        val url = resolveArtwork(server, reference) ?: return null
        val token = credentials.token.takeIf { server.sameOrigin(url) }
        return HaHttp.get(url, token) { it.readImage() }
    }

    suspend fun personAvatar(reference: String): ByteArray? {
        val url = resolvePersonAvatar(credentials.server, reference) ?: return null
        return HaHttp.get(url, credentials.token) { it.readImage() }
    }

    suspend fun photoAlbum(id: String?): PhotoAlbum = browsePhotoAlbum(id, ::request)

    suspend fun photo(id: String): ByteArray? {
        val path = resolvePhoto(id, ::request) ?: return null
        val url = resolvePhotoUrl(credentials.server, id, path) ?: return null
        return HaHttp.get(url, credentials.token) { it.readImage(MAX_PHOTO_BYTES) }
    }

    suspend fun intercomSource(stream: String): HttpUrl {
        require(validIntercomStream(stream))
        val path = "/api/webrtc/ws"
        val response = request("auth/sign_path", JSONObject().put("path", path).put("expires", 30))
        val signed = response.optJSONObject("result")?.text("path") ?: throw HaProblem("Intercom is unavailable")
        return signedIntercomUrl(credentials.server, signed, stream)
            ?: throw HaProblem("Home Assistant returned an invalid intercom address")
    }

    suspend fun cameraCapabilities(entity: String): CameraCapabilities {
        require(validEntity(entity) && entity.startsWith("camera."))
        val now = System.nanoTime()
        cameraCapabilities[entity]?.let { (time, value) ->
            if (now - time < 120_000_000_000L) return value
        }
        val response = request("camera/capabilities", JSONObject().put("entity_id", entity))
        val result = response.optJSONObject("result") ?: return CameraCapabilities.None
        return CameraCapabilities.from(result).also { cameraCapabilities[entity] = now to it }
    }

    suspend fun nativeCamera(entity: String, wantsTalk: Boolean): NativeCameraSignaling {
        require(validEntity(entity) && entity.startsWith("camera."))
        val socket = connection.currentSocket()
        val reply = socket.request("camera/webrtc/get_client_config", JSONObject().put("entity_id", entity))
        val configuration = reply.optJSONObject("result") ?: throw HaProblem("Home Assistant didn't configure WebRTC")
        return NativeCameraSignaling(socket, entity, configuration,
            if (wantsTalk) CameraAudioOffer.TWO_WAY else CameraAudioOffer.LISTEN)
    }

    suspend fun cameraStream(entity: String): CameraStream {
        require(validEntity(entity) && entity.startsWith("camera."))
        val response = request("camera/stream", JSONObject().put("entity_id", entity).put("format", "hls"))
        val path = response.optJSONObject("result")?.text("url")
        if (path == null || !streamPath.matches(path)) throw HaProblem("This camera has no supported live stream")
        val url = checkNotNull(credentials.server.resolve(path.removePrefix("/")))
        return CameraStream(url, videoClient)
    }

    private suspend fun request(type: String, data: JSONObject = JSONObject()): JSONObject =
        connection.request(type, data)

    companion object {
        suspend fun validate(credentials: HaCredentials) {
            val url = checkNotNull(credentials.server.resolve("api/"))
            HaHttp.get(url, credentials.token) { response ->
                if (!response.isSuccessful) throw HaProblem(
                    if (response.code == 401 || response.code == 403) "Home Assistant rejected the token"
                    else "Couldn't open the Home Assistant API"
                )
                if (response.readJson()?.optString("message") != "API running.") {
                    throw HaProblem("This address didn't return the Home Assistant API")
                }
            }
        }
    }
}

private fun JSONObject.entityServiceRows(entity: String, field: String): JSONArray =
    optJSONObject("result")?.optJSONObject("response")?.optJSONObject(entity)?.optJSONArray(field)
        ?: throw HaProblem("Home Assistant couldn't load this list")

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
