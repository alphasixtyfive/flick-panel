package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.PersonLocation
import app.flickpanel.ha.personStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonLocationTest {
    @Test
    fun boundedNumericPairsKeepTheirValuesAndStayOutOfDiagnostics() {
        val example = person("not_home", 0.000001, -125.0)
        val location = checkNotNull(PersonLocation.from(example))
        assertEquals(0.000001, location.latitude, 0.0)
        assertEquals(-125.0, location.longitude, 0.0)
        assertEquals("PersonLocation", location.toString())
        val boundary = checkNotNull(PersonLocation.from(person("home", -90, 180)))
        assertEquals(-90.0, boundary.latitude, 0.0)
        assertEquals(180.0, boundary.longitude, 0.0)
    }

    @Test
    fun incompleteInvalidOrUnavailableLocationsNeverBecomeMapLinks() {
        for (entity in listOf(
            person("home", 91, 0), person("home", 0, -181),
            person("home", "12", 12), person("home", 12, false),
            person("home", null, 12), person("home", 12, null),
            person("unknown", 12, 12), person("unavailable", 12, 12),
            person("home", 12, 12).copy(id = "sensor.other"),
        )) assertNull(PersonLocation.from(entity))
    }

    @Test
    fun zoneStatusIsReadableWithoutLocationCoordinates() {
        assertEquals("Home", person("home", null, null).personStatus())
        assertEquals("Away", person("not_home", null, null).personStatus())
        assertEquals("Example zone", person("example_zone", null, null).personStatus())
        assertNull(person("unavailable", null, null).personStatus())
    }

    private fun person(state: String, latitude: Any?, longitude: Any?) = Entity(
        "person.example", state, JSONObject().put("latitude", latitude).put("longitude", longitude),
    )
}
