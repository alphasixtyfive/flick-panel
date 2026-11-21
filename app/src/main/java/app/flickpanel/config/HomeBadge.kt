package app.flickpanel.config

internal data class HomeBadge(
    val entity: String,
    val label: String?,
    val activeStates: List<String>,
)

internal fun validBadgeSource(id: String): Boolean = when (id.substringBefore('.')) {
    "binary_sensor", "sensor", "input_boolean" -> validEntity(id)
    else -> false
}

internal fun badgeStates(values: Iterable<String>): List<String> = values
    .map { it.trim().lowercase() }
    .filter { it.isNotEmpty() && it.length <= 80 && it != "unknown" && it != "unavailable" }
    .distinct()
    .take(12)
