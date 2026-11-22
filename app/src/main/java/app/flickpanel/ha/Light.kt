package app.flickpanel.ha

import kotlin.math.abs
import org.json.JSONArray

private val colourModes = setOf("hs", "xy", "rgb", "rgbw", "rgbww")

internal val Entity.canDim: Boolean
    get() = domain == "light" && (state != "off" && brightness != null ||
        supportsLightMode { it.isNotEmpty() && it != "onoff" && it != "unknown" })

internal val Entity.supportsColour: Boolean
    get() = supportsLightMode { it in colourModes }

internal val Entity.colourTemperatures: IntRange?
    get() {
        if (!supportsLightMode { it == "color_temp" }) return null
        val warm = attributes.optInt("min_color_temp_kelvin", 2000)
        val cool = attributes.optInt("max_color_temp_kelvin", 6500)
        return (warm..cool).takeIf { warm > 0 && cool > warm }
    }

private inline fun Entity.supportsLightMode(matches: (String) -> Boolean): Boolean {
    if (domain != "light") return false
    val modes = attributes.optJSONArray("supported_color_modes") ?: return false
    return (0 until modes.length()).any { matches(modes.optString(it)) }
}

internal data class LightColour(val red: Int, val green: Int, val blue: Int) {
    fun toJson() = JSONArray().put(red).put(green).put(blue)

    fun isCloseTo(other: LightColour): Boolean =
        abs(red - other.red) <= 12 && abs(green - other.green) <= 12 && abs(blue - other.blue) <= 12
}

internal val Entity.lightColour: LightColour?
    get() {
        if (state != "on" || !supportsColour || attributes.optString("color_mode") !in colourModes) return null
        val rgb = attributes.optJSONArray("rgb_color") ?: return null
        val red = rgb.optInt(0, -1)
        val green = rgb.optInt(1, -1)
        val blue = rgb.optInt(2, -1)
        if (red !in 0..255 || green !in 0..255 || blue !in 0..255) return null
        return LightColour(red, green, blue)
    }
