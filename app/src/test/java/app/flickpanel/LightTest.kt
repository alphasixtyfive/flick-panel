package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.LightColour
import app.flickpanel.ha.canDim
import app.flickpanel.ha.colourTemperatures
import app.flickpanel.ha.lightColour
import app.flickpanel.ha.supportsColour
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightTest {
    @Test
    fun capabilitiesAreAvailableEvenWhenTheLightIsOff() {
        val light = Entity("light.lamp", "off", JSONObject("""{
            "supported_color_modes": ["hs", "color_temp"],
            "min_color_temp_kelvin": 2200,
            "max_color_temp_kelvin": 6000
        }"""))
        assertTrue(light.canDim)
        assertTrue(light.supportsColour)
        assertEquals(2200..6000, light.colourTemperatures)
        assertNull(light.lightColour)
        val onOff = light.copy(attributes = JSONObject("""{"supported_color_modes":["onoff"]}"""))
        assertFalse(onOff.canDim)
        assertFalse(onOff.supportsColour)
        assertNull(onOff.colourTemperatures)
    }

    @Test
    fun selectedColourComesFromALiveColourMode() {
        val light = Entity("light.strip", "on", JSONObject("""{
            "supported_color_modes": ["rgb", "color_temp"], "color_mode": "rgb", "rgb_color": [80, 122, 251]
        }"""))
        val blue = LightColour(80, 121, 255)
        assertTrue(light.lightColour!!.isCloseTo(blue))
        assertEquals("[80,121,255]", blue.toJson().toString())
        assertNull(light.copy(state = "off").lightColour)
        val white = light.copy(attributes = JSONObject(light.attributes.toString()).put("color_mode", "color_temp"))
        assertNull(white.lightColour)
    }
}
