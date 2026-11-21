package app.flickpanel.ha

import app.flickpanel.config.validEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun reduceEntities(
    previous: Map<String, Entity>, event: JSONObject, selected: Set<String>, initial: Boolean
): Map<String, Entity> {
    val before = if (initial) emptyMap() else previous
    var updated: MutableMap<String, Entity>? = null
    fun edit() = updated ?: before.toMutableMap().also { updated = it }

    if (before.keys.any { it !in selected }) edit().keys.retainAll(selected)
    event.optJSONObject("a")?.let { added ->
        for (id in added.keys()) {
            if (id !in selected || !validEntity(id)) continue
            val record = added.optJSONObject(id) ?: continue
            edit()[id] = Entity(id, record.optString("s"), record.optJSONObject("a") ?: JSONObject())
        }
    }
    val removed = event.optJSONArray("r")
    if (removed != null) for (index in 0 until removed.length()) {
        val id = removed.optString(index)
        if (id in (updated ?: before)) edit().remove(id)
    }
    event.optJSONObject("c")?.let { changes ->
        for (id in changes.keys()) {
            val current = (updated ?: before)[id] ?: continue
            val change = changes.optJSONObject(id) ?: continue
            val positive = change.optJSONObject("+")
            val state = positive?.optString("s", current.state) ?: current.state
            val attributes = patchAttributes(current.attributes, positive?.optJSONObject("a"),
                change.optJSONObject("-")?.optJSONArray("a"))
            if (state != current.state || attributes !== current.attributes) {
                edit()[id] = current.copy(state = state, attributes = attributes)
            }
        }
    }
    return updated ?: before
}

private fun patchAttributes(before: JSONObject, added: JSONObject?, removed: JSONArray?): JSONObject {
    val changesValue = added?.keys()?.asSequence()?.any { key -> !before.has(key) || before.opt(key) != added.opt(key) } == true
    val removesValue = removed != null && (0 until removed.length()).any { before.has(removed.optString(it)) }
    if (!changesValue && !removesValue) return before
    return JSONObject().apply {
        for (key in before.keys()) put(key, before.get(key))
        if (added != null) for (key in added.keys()) put(key, added.get(key))
        if (removed != null) for (index in 0 until removed.length()) remove(removed.optString(index))
    }
}
