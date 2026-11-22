package app.flickpanel

import app.flickpanel.camera.IntercomConnectionProblem
import app.flickpanel.camera.connectIntercom
import app.flickpanel.config.CameraActionKind
import app.flickpanel.ha.Entity
import app.flickpanel.ha.cameraActionKind
import app.flickpanel.ha.cameraActionReady
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntercomRecoveryTest {
    @Test
    fun aTransientReadOnlyFailureGetsOneRetry() = runBlocking {
        var attempts = 0
        var retries = 0
        connectIntercom(allowRetry = true, onRetry = { retries++ }) {
            if (++attempts == 1) throw IntercomConnectionProblem("First frame timed out")
        }
        assertEquals(2, attempts)
        assertEquals(1, retries)
    }

    @Test
    fun retryStopsAfterTheSecondAttempt() = runBlocking {
        var attempts = 0
        val failure = runCatching {
            connectIntercom(allowRetry = true, onRetry = {}) {
                attempts++
                throw IntercomConnectionProblem("Transport interrupted")
            }
        }.exceptionOrNull()
        assertTrue(failure is IntercomConnectionProblem)
        assertEquals(2, attempts)
    }

    @Test
    fun talkingAndPermanentErrorsDoNotRetry() = runBlocking {
        for ((allowRetry, temporary) in listOf(false to true, true to false)) {
            var attempts = 0
            var retries = 0
            val failure = runCatching {
                connectIntercom(allowRetry, onRetry = { retries++ }) {
                    attempts++
                    throw IntercomConnectionProblem("Unavailable", retryable = temporary)
                }
            }.exceptionOrNull()
            assertTrue(failure is IntercomConnectionProblem)
            assertEquals(1, attempts)
            assertEquals(0, retries)
        }
    }

    @Test
    fun gateSelectionAndExecutionUseTheSameCapability() {
        val gate = Entity("cover.entry", "closed", JSONObject("""{"device_class":"gate","supported_features":1}"""))
        assertEquals(CameraActionKind.OPEN_GATE, gate.cameraActionKind)
        assertTrue(gate.cameraActionReady)
        assertFalse(gate.copy(state = "open").cameraActionReady)

        val unsupported = gate.copy(attributes = JSONObject("""{"device_class":"gate","supported_features":2}"""))
        assertNull(unsupported.cameraActionKind)
        assertFalse(unsupported.cameraActionReady)
        assertEquals(CameraActionKind.RUN, Entity("script.announce", "off").cameraActionKind)
        assertEquals(CameraActionKind.TOGGLE, Entity("light.entry", "off").cameraActionKind)
    }
}
