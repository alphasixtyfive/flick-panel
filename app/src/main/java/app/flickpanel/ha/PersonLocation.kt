package app.flickpanel.ha

import okhttp3.HttpUrl

internal class PersonLocation private constructor(val latitude: Double, val longitude: Double) {

    override fun toString() = "PersonLocation"

    companion object {
        fun from(entity: Entity): PersonLocation? {
            if (entity.domain != "person" || !entity.available) return null
            val latitude = (entity.attributes.opt("latitude") as? Number)?.toDouble() ?: return null
            val longitude = (entity.attributes.opt("longitude") as? Number)?.toDouble() ?: return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return PersonLocation(latitude, longitude)
        }
    }
}

internal fun Entity.personStatus(): String? {
    if (domain != "person" || !available) return null
    return when (state) {
        "home" -> "Home"
        "not_home" -> "Away"
        else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

internal val Entity.personPicture: String?
    get() = (attributes.opt("entity_picture") as? String)?.takeIf { domain == "person" && it.isNotBlank() }

internal fun resolvePersonAvatar(server: HttpUrl, reference: String): HttpUrl? {
    if (reference.length !in 1..2048 || reference.any(Char::isISOControl)) return null
    val url = server.resolve(reference)?.takeIf(server::sameOrigin) ?: return null
    val path = url.encodedPath.removePrefix(server.encodedPath).removePrefix("/")
    return url.takeIf { path.startsWith("api/image/serve/") || path.startsWith("local/") }
}
