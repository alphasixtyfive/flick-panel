package app.flickpanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInputTest {
    @Test fun firstRunIdentifiesEachInvalidField() {
        val input = validateConnectionInput("home.example", "too short", "23", "24", needsPin = true)
        assertEquals(ConnectionField.entries, input.errors.keys.toList())
    }

    @Test fun tokenReplacementOnlyChecksConnectionFields() {
        val input = validateConnectionInput(" http://192.168.1.5:8123 ", " test-fixture-token ", "", "", needsPin = false)
        assertTrue(input.errors.isEmpty())
        assertEquals("http://192.168.1.5:8123/", input.server.toString())
    }

    @Test fun firstRunRequiresMatchingPinsAndTheServerStillEnforcesHttps() {
        val input = validateConnectionInput("http://home.example", "test-fixture-token", "1234", "1235", needsPin = true)
        assertEquals(listOf(ConnectionField.ADDRESS, ConnectionField.CONFIRMATION), input.errors.keys.toList())
    }
}
