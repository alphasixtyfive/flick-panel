package app.flickpanel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class PanelImages {
    val snapshots = ImageCache(byteLimit = 12 * 1024 * 1024, maxEdge = 1440, lifetimeMillis = 24_000)
    val artwork = ImageCache(byteLimit = 8 * 1024 * 1024, maxEdge = 480, lifetimeMillis = 30 * 60_000)
}

internal class ImageCache(byteLimit: Int, private val maxEdge: Int, private val lifetimeMillis: Long) {
    private data class CachedImage(val bitmap: Bitmap, val fetchedAt: Long)

    private val images = object : LruCache<String, CachedImage>(byteLimit) {
        override fun sizeOf(key: String, value: CachedImage) = value.bitmap.allocationByteCount
    }
    private val requests = Semaphore(2)

    fun cached(key: String): Bitmap? = images[key]?.bitmap

    fun fresh(key: String): Bitmap? = images[key]
        ?.takeIf { SystemClock.elapsedRealtime() - it.fetchedAt < lifetimeMillis }
        ?.bitmap

    suspend fun load(key: String, fetch: suspend () -> ByteArray?): Bitmap? {
        fresh(key)?.let { return it }
        return requests.withPermit {
            fresh(key)?.let { return@withPermit it }
            val data = fetch() ?: return@withPermit null
            val bitmap = decodePanelImage(data, maxEdge) ?: return@withPermit null
            images.put(key, CachedImage(bitmap, SystemClock.elapsedRealtime()))
            bitmap
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
