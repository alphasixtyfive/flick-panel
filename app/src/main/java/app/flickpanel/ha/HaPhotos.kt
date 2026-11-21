package app.flickpanel.ha

import okhttp3.HttpUrl
import org.json.JSONObject

private val photoSources = mapOf(
    "media-source://media_source" to "media/",
    "media-source://synology_dsm" to "synology_dsm/",
)

internal data class PhotoFolder(val id: String, val title: String)

internal data class PhotoAlbum(
    val folders: List<PhotoFolder>,
    val photos: List<String>,
)

internal fun isPhotoSourceId(id: String): Boolean =
    id.length <= 2048 && id.none(Char::isISOControl) && photoSources.keys.any { inSource(id, it) }

internal suspend fun browsePhotoAlbum(
    id: String?,
    request: suspend (String, JSONObject) -> JSONObject,
): PhotoAlbum {
    require(id == null || isPhotoSourceId(id))
    val result = request("media_source/browse_media", JSONObject().put("media_content_id", id))
        .optJSONObject("result") ?: throw HaProblem("Home Assistant did not return a photo folder")
    val folders = mutableListOf<PhotoFolder>()
    val photos = mutableListOf<String>()
    for (item in result.optJSONArray("children").objects()) {
        if (item.optBoolean("can_expand")) {
            item.photoFolder()?.let(folders::add)
        } else if (item.optBoolean("can_play") && isPhotoType(item.text("media_content_type"))) {
            item.text("media_content_id")?.takeIf(::isPhotoSourceId)?.let(photos::add)
        }
    }
    return PhotoAlbum(folders.distinctBy(PhotoFolder::id), photos.distinct())
}

internal suspend fun resolvePhoto(
    id: String,
    request: suspend (String, JSONObject) -> JSONObject,
): String? {
    require(isPhotoSourceId(id))
    val result = request("media_source/resolve_media", JSONObject().put("media_content_id", id))
        .optJSONObject("result") ?: return null
    return result.text("url")?.takeIf { it.length <= 8192 && isPhotoType(result.text("mime_type")) }
}

internal fun resolvePhotoUrl(server: HttpUrl, id: String, reference: String): HttpUrl? {
    val prefix = photoSources.entries.firstOrNull { (source, _) -> inSource(id, source) }?.value ?: return null
    val url = server.resolve(reference)?.takeIf(server::sameOrigin) ?: return null
    return url.takeIf {
        val path = it.encodedPath
        path.startsWith(server.encodedPath + prefix) || path.startsWith("/$prefix")
    }
}

private fun inSource(id: String, source: String): Boolean =
    id == source || id.startsWith(source) && id.getOrNull(source.length) == '/'

private fun JSONObject.photoFolder(): PhotoFolder? {
    val id = text("media_content_id")?.takeIf(::isPhotoSourceId) ?: return null
    val title = text("title")?.take(140) ?: return null
    return PhotoFolder(id, title)
}

private fun isPhotoType(type: String?): Boolean = type?.startsWith("image/") == true && type != "image/svg+xml"
