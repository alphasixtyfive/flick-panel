package app.flickpanel

import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRequestsTest {
    @Test
    fun sharedImageKeepsLoadingUntilTheLastWaiterLeaves() = runBlocking {
        withTimeout(5_000) {
            val requests = ImageRequests(this)
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            var fetches = 0
            suspend fun fetch(): Bitmap? {
                fetches++
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            }

            val first = launch(start = CoroutineStart.UNDISPATCHED) { requests.load("preview", ::fetch) }
            val second = launch(start = CoroutineStart.UNDISPATCHED) { requests.load("preview", ::fetch) }
            started.await()
            first.cancelAndJoin()
            assertEquals(1, fetches)
            assertFalse(stopped.isCompleted)

            second.cancelAndJoin()
            stopped.await()
        }
    }

    @Test
    fun abandonedImageCanBeRequestedAgainImmediately() = runBlocking {
        withTimeout(5_000) {
            val requests = ImageRequests(this)
            val started = CompletableDeferred<Unit>()
            val first = launch {
                requests.load("preview") {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            started.await()
            first.cancelAndJoin()

            var fetchedAgain = false
            requests.load("preview") {
                fetchedAgain = true
                null
            }
            assertTrue(fetchedAgain)
        }
    }
}
