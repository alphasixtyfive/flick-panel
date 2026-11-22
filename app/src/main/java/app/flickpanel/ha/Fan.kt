package app.flickpanel.ha

import kotlin.math.roundToInt

private const val fanSetSpeed = 1L
private const val fanTurnOff = 16L
private const val fanTurnOn = 32L

internal val Entity.canSetFanSpeed
    get() = domain == "fan" && attributes.optLong("supported_features") and fanSetSpeed != 0L

internal val Entity.fanSpeed: Int?
    get() = when {
        domain != "fan" || !available -> null
        state == "off" -> 0
        else -> attributes.optInt("percentage", -1).takeIf { it in 0..100 }
    }

internal fun Entity.fanPowerService(turnOn: Boolean): String? {
    if (domain != "fan" || !available) return null
    val feature = if (turnOn) fanTurnOn else fanTurnOff
    if (attributes.optLong("supported_features") and feature == 0L) return null
    return if (turnOn) "turn_on" else "turn_off"
}

internal fun Entity.fanSpeedTarget(level: Int): Int? {
    if (!canSetFanSpeed || !available || level !in 0..100) return null
    val step = attributes.optDouble("percentage_step")
    if (step !in 1.0..100.0 || level == 0 || level == 100) return level
    return ((level / step).roundToInt().coerceAtLeast(1) * step).roundToInt().coerceIn(1, 100)
}
