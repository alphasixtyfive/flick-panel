package app.flickpanel.ha

import app.flickpanel.config.validAreaId
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal enum class VacuumAction(val feature: Long, val service: String) {
    START(8192L, "start"), CLEAN_AREA(16384L, "clean_area"),
    PAUSE(4L, "pause"), STOP(8L, "stop"), DOCK(16L, "return_to_base");

    fun confirmed(entity: Entity): Boolean = when (this) {
        START, CLEAN_AREA -> entity.state == "cleaning"
        PAUSE -> entity.state == "paused"
        STOP -> entity.state in setOf("idle", "docked", "returning")
        DOCK -> entity.state in setOf("returning", "docked")
    }

    fun data(rooms: List<String>): JSONObject = JSONObject().apply {
        if (this@VacuumAction == CLEAN_AREA) {
            require(rooms.isNotEmpty() && rooms.all(::validAreaId))
            put("cleaning_area_id", JSONArray(rooms.distinct()))
        }
    }
}

internal val Entity.vacuumReady: Boolean get() = state == "idle" || state == "docked"

internal fun Entity.supportsVacuum(action: VacuumAction): Boolean =
    domain == "vacuum" && attributes.optLong("supported_features") and action.feature != 0L

internal fun Entity.canRunVacuum(action: VacuumAction): Boolean = supportsVacuum(action) && when (action) {
    VacuumAction.START -> vacuumReady || state == "paused"
    VacuumAction.CLEAN_AREA -> vacuumReady
    VacuumAction.PAUSE -> state == "cleaning"
    VacuumAction.STOP -> state == "cleaning" || state == "paused"
    VacuumAction.DOCK -> state in setOf("idle", "cleaning", "paused", "error")
}

internal fun Entity.vacuumPrimaryAction(hasSelectedRooms: Boolean): VacuumAction? = when {
    state == "cleaning" -> VacuumAction.PAUSE
    state == "paused" -> VacuumAction.START
    hasSelectedRooms -> VacuumAction.CLEAN_AREA
    else -> VacuumAction.START
}.takeIf(::canRunVacuum)

internal fun vacuumAreaIds(registryEntry: JSONObject?): Set<String> =
    registryEntry?.optJSONObject("options")?.optJSONObject("vacuum")
        ?.optJSONObject("area_mapping")?.keys()?.asSequence()?.filter(::validAreaId)?.toSet().orEmpty()

internal fun vacuumBatteryPercent(vacuum: Entity?, sensor: Entity?): Int? {
    val linkedLevel = sensor?.state?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    return (linkedLevel ?: vacuum?.attributes?.optDouble("battery_level")?.takeIf { it in 0.0..100.0 })?.roundToInt()
}

internal fun vacuumStatus(entity: Entity?): String = when (entity?.state) {
    "docked" -> "Docked"
    "idle" -> "Idle"
    "cleaning" -> "Cleaning"
    "paused" -> "Paused"
    "returning" -> "Returning"
    "error" -> "Needs attention"
    else -> "Unavailable"
}
