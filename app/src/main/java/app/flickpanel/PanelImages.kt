package app.flickpanel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val MiB = 1024 * 1024

internal class PanelImages(parent: CoroutineScope, lowMemory: Boolean) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    val snapshots = ImageCache(scope, byteLimit = (if (lowMemory) 6 else 12) * MiB,
        maxEdge = if (lowMemory) 1080 else 1440, lifetimeMillis = 24_000)
    val artwork = ImageCache(scope, byteLimit = (if (lowMemory) 3 else 8) * MiB,
        maxEdge = if (lowMemory) 320 else 480, lifetimeMillis = 30 * 60_000)
    val photos = ImageCache(scope, byteLimit = (if (lowMemory) 6 else 12) * MiB,
        maxEdge = if (lowMemory) 1200 else 2000, lifetimeMillis = 15 * 60_000)

    fun close() {
        job.cancel()
        snapshots.clear()
        artwork.clear()
        photos.clear()
    }
}

internal class ImageCache(
    scope: CoroutineScope, byteLimit: Int, private val maxEdge: Int, private val lifetimeMillis: Long,
) {
    private data class CachedImage(val bitmap: Bitmap, val fetchedAt: Long)

    private val images = object : LruCache<String, CachedImage>(byteLimit) {
        override fun sizeOf(key: String, value: CachedImage) = value.bitmap.allocationByteCount
    }
    private val fetchSlots = Semaphore(2)
    private val requests = ImageRequests(scope)

    fun cached(key: String): Bitmap? = images[key]?.bitmap

    fun fresh(key: String): Bitmap? = images[key]
        ?.takeIf { SystemClock.elapsedRealtime() - it.fetchedAt < lifetimeMillis }
        ?.bitmap

    fun clear() = images.evictAll()

    suspend fun load(key: String, decodeEdge: Int = maxEdge, fetch: suspend () -> ByteArray?): Bitmap? {
        fresh(key)?.let { return it }
        return requests.load(key) {
            fetchSlots.withPermit {
                fresh(key)?.let { return@withPermit it }
                val data = fetch() ?: return@withPermit null
                val bitmap = decodePanelImage(data, minOf(maxEdge, decodeEdge)) ?: return@withPermit null
                currentCoroutineContext().ensureActive()
                images.put(key, CachedImage(bitmap, SystemClock.elapsedRealtime()))
                bitmap
            }
        }
    }
}

internal class ImageRequests(private val scope: CoroutineScope) {
    private class Request(val job: Deferred<Bitmap?>, var waiters: Int = 0)

    private val inFlight = mutableMapOf<String, Request>()

    suspend fun load(key: String, fetch: suspend () -> Bitmap?): Bitmap? {
        val request = synchronized(inFlight) {
            val pending = inFlight[key] ?: Request(scope.async(start = CoroutineStart.LAZY) { fetch() }).also { created ->
                inFlight[key] = created
                created.job.invokeOnCompletion { synchronized(inFlight) { inFlight.remove(key, created) } }
            }
            pending.waiters++
            pending
        }
        try {
            return request.job.await()
        } finally {
            val abandoned = synchronized(inFlight) {
                request.waiters--
                request.waiters == 0 && inFlight.remove(key, request)
            }
            if (abandoned) request.job.cancel()
        }
    }
}

internal suspend fun decodePanelImage(data: ByteArray, maxEdge: Int): Bitmap? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth !in 1..12_000 || bounds.outHeight !in 1..12_000) return@withContext null
    val edge = maxOf(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (edge / inSampleSize > maxEdge) inSampleSize *= 2
    }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
}
