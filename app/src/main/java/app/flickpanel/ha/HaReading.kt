package app.flickpanel.ha

private const val MaxReadingLength = 24_000
private const val MaxPreviewLength = 280

private val readerMetadata = setOf(
    "attribution", "device_class", "entity_picture", "friendly_name", "icon", "mode",
    "state_class", "unit_of_measurement", "authorization", "api_key", "private_key",
)
private val readerSensitiveWords = setOf("password", "token", "secret", "credential", "credentials")
private val readerCamelCase = Regex("([a-z0-9])([A-Z])")
private val readerAttributeSeparators = Regex("[^a-z0-9]+")
private val previewWhitespace = Regex("[\\s\\p{Z}]+")

internal data class ReadingContent(val text: String, val truncated: Boolean) {
    val preview: String
        get() {
            val summary = text.replace(previewWhitespace, " ")
            return if (summary.length > MaxPreviewLength) {
                summary.safePrefix(MaxPreviewLength).trimEnd() + "…"
            } else summary
        }
}

internal val Entity.supportsReader: Boolean
    get() = when (domain) {
        "sensor" -> true
        "input_text" -> !"password".equals(attributes.opt("mode") as? String, ignoreCase = true)
        else -> false
    }

internal fun validReaderAttribute(name: String): Boolean {
    if (name.isBlank() || name.length > 128 || name.any(Char::isISOControl)) return false
    val normalized = name.trim()
        .replace(readerCamelCase, "$1_$2")
        .lowercase()
        .replace(readerAttributeSeparators, "_")
        .trim('_')
    return normalized !in readerMetadata && normalized.split('_').none { it in readerSensitiveWords }
}

internal fun Entity.readerAttributes(): List<String> {
    if (!supportsReader) return emptyList()
    return attributes.keys().asSequence()
        .filter(::hasReadingText)
        .sortedBy(String::lowercase)
        .take(48)
        .toList()
}

internal val Entity.hasReaderAttribute: Boolean
    get() = supportsReader && attributes.keys().asSequence().any(::hasReadingText)

private fun Entity.hasReadingText(attribute: String): Boolean =
    validReaderAttribute(attribute) && (attributes.opt(attribute) as? String)?.isNotBlank() == true

internal fun readContent(entity: Entity?, attribute: String?): ReadingContent? {
    if (entity == null || !entity.available || !entity.supportsReader) return null
    val source = if (attribute == null) entity.state else {
        if (!validReaderAttribute(attribute)) return null
        entity.attributes.opt(attribute) as? String ?: return null
    }
    val text = source.safePrefix(MaxReadingLength)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .trim()
    return text.takeIf(String::isNotEmpty)?.let { ReadingContent(it, source.length > MaxReadingLength) }
}

private fun String.safePrefix(limit: Int): String {
    if (length <= limit) return this
    val end = if (this[limit - 1].isHighSurrogate() && this[limit].isLowSurrogate()) limit - 1 else limit
    return substring(0, end)
}
