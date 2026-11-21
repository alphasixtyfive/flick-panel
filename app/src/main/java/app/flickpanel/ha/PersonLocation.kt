package app.flickpanel.ha

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
