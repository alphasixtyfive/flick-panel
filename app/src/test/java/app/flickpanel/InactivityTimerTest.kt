package app.flickpanel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Test

class InactivityTimerTest {
    @Test
    fun activityExtendsTheDeadlineOfAnAlreadyScheduledTimer() = runBlocking {
        var elapsed = 0L
        val expired = CompletableDeferred<Unit>()
        val timer = InactivityTimer(this, { elapsed }) { expired.complete(Unit) }

        timer.restart(50)
        yield()
        elapsed = 49
        timer.restart(50)
        elapsed = 50
        delay(55)
        assertFalse(expired.isCompleted)

        elapsed = 99
        withTimeout(2_000) { expired.await() }
    }

    @Test
    fun cancelPreventsAnExpiredSessionFromActing() = runBlocking {
        var elapsed = 0L
        val expired = CompletableDeferred<Unit>()
        val timer = InactivityTimer(this, { elapsed }) { expired.complete(Unit) }

        timer.restart(20)
        yield()
        timer.cancel()
        elapsed = 100
        delay(25)
        assertFalse(expired.isCompleted)
    }
}
