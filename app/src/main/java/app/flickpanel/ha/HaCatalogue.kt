package app.flickpanel.ha

import app.flickpanel.config.validEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun loadCatalogue(request: suspend (String) -> JSONObject): Catalogue = coroutineScope {
    val states = async { request("get_states").optJSONArray("result") }
    val registry = async { registryForDisplay(request) }
    val devices = async {
        optionalList(request, "config/device_registry/list").objects().associateBy { it.text("id") }
    }
    val areas = async {
        decodeAreas(optionalList(request, "config/area_registry/list"))
    }
    val metadata = registry.await()
    val hardware = devices.await()
    val entries = states.await().objects().mapNotNull { row ->
        val id = row.optString("entity_id").takeIf(::validEntity) ?: return@mapNotNull null
        val entity = Entity(id, row.optString("state"), row.optJSONObject("attributes") ?: JSONObject())
        if (entity.kinds().isEmpty()) return@mapNotNull null
        val entry = metadata[id]
        Entry(
            entity,
            entry?.area ?: entry?.device?.let { hardware[it]?.text("area_id") },
            entry?.diagnostic == true,
            entry?.hidden != true,
        )
    }.sortedBy { it.entity.name.lowercase() }.toList()
    Catalogue(entries, areas.await())
}

internal fun decodeAreas(rows: JSONArray?): List<Area> = rows.objects().mapNotNull { row ->
    val id = row.text("area_id") ?: return@mapNotNull null
    Area(id, row.text("name") ?: id)
}.sortedBy { it.name.lowercase() }.toList()

private data class RegistryEntry(val area: String?, val device: String?, val diagnostic: Boolean, val hidden: Boolean)

private suspend fun registryForDisplay(request: suspend (String) -> JSONObject): Map<String, RegistryEntry> {
    val compact = try {
        request("config/entity_registry/list_for_display").optJSONObject("result")?.optJSONArray("entities")
    } catch (_: HaProblem) {
        null
    }
    return if (compact != null) registry(compact, "ei") { row ->
        RegistryEntry(row.text("ai"), row.text("di"), !row.isNull("ec"), row.optBoolean("hb"))
    } else registry(optionalList(request, "config/entity_registry/list"), "entity_id") { row ->
        RegistryEntry(
            row.text("area_id"), row.text("device_id"), row.text("entity_category") != null,
            row.text("hidden_by") != null || row.text("disabled_by") != null,
        )
    }
}

private inline fun registry(
    rows: JSONArray?, idField: String, decode: (JSONObject) -> RegistryEntry,
): Map<String, RegistryEntry> = buildMap {
    for (row in rows.objects()) {
        val id = row.text(idField) ?: continue
        put(id, decode(row))
    }
}

private suspend fun optionalList(request: suspend (String) -> JSONObject, type: String): JSONArray? =
    try {
        request(type).optJSONArray("result")
    } catch (_: HaProblem) {
        null
    }

internal fun JSONArray?.objects(): Sequence<JSONObject> =
    if (this == null) emptySequence() else (0 until length()).asSequence().mapNotNull(::optJSONObject)
