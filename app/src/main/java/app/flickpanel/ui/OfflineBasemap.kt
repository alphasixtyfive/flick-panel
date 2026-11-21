package app.flickpanel.ui

import android.content.res.AssetManager
import androidx.annotation.MainThread
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import java.io.DataInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class BasemapPlace(val point: Offset, val rank: Int, val name: String)

internal data class OfflineBasemap(
    val countries: Path,
    val lakes: Path,
    val places: List<BasemapPlace>,
    val regions: List<OfflineRegion>,
)

internal data class OfflineRegion(val asset: String, val bounds: Rect)
internal data class OfflineDetail(val cells: List<DetailCell>, val roadNames: List<BasemapPlace>)
internal data class DetailCell(val bounds: Rect, val layers: List<Path>)
internal enum class DetailLayer(val closed: Boolean = false) { PARK(true), WATER(true), RIVER, LOCAL_ROAD, MAIN_ROAD }

@MainThread
internal class OfflineMapAssets(private val assets: AssetManager, private val retainDetail: Boolean) {
    var cachedBasemap: OfflineBasemap? = null
        private set
    private var recentDetail: Pair<OfflineRegion, OfflineDetail>? = null
    private var generation = 0

    fun cachedDetail(region: OfflineRegion): OfflineDetail? =
        recentDetail?.takeIf { it.first == region }?.second

    suspend fun basemap(): OfflineBasemap {
        cachedBasemap?.let { return it }
        val started = generation
        val map = withContext(Dispatchers.IO) { readOfflineBasemap(assets) { ensureActive() } }
        if (generation == started) cachedBasemap = map
        return map
    }

    suspend fun detail(region: OfflineRegion): OfflineDetail {
        cachedDetail(region)?.let { return it }
        val started = generation
        val map = withContext(Dispatchers.IO) { readOfflineDetail(assets, region) { ensureActive() } }
        if (retainDetail && generation == started) recentDetail = region to map
        return map
    }

    fun clear() {
        generation++
        cachedBasemap = null
        recentDetail = null
    }
}

private fun readOfflineBasemap(assets: AssetManager, checkActive: () -> Unit): OfflineBasemap =
    DataInputStream(assets.open("offline_map/world.fpm").buffered()).use { input ->
        if (input.readInt() != 0x46504d31) throw IOException("Unsupported bundled map format")
        val countries = input.readLayer(checkActive)
        val lakes = input.readLayer(checkActive)
        val places = List(input.readCount(2_000)) {
            val point = input.readPoint()
            val rank = input.readUnsignedByte()
            val length = input.readUnsignedShort()
            if (length !in 1..200) throw IOException("Invalid map label")
            val nameBytes = ByteArray(length).also(input::readFully)
            BasemapPlace(point, rank, nameBytes.toString(Charsets.UTF_8))
        }
        if (input.read() != -1) throw IOException("Unexpected map data")
        OfflineBasemap(countries, lakes, places, offlineRegions(assets))
    }

private fun DataInputStream.readLayer(checkActive: () -> Unit): Path {
    var totalPoints = 0
    return Path().apply {
        fillType = PathFillType.EvenOdd
        repeat(readCount(5_000)) {
            checkActive()
            val points = readCount(250_000, minimum = 3)
            totalPoints += points
            if (totalPoints > 250_000) throw IOException("Bundled map layer is too large")
            val first = readPoint()
            moveTo(first.x, first.y)
            repeat(points - 1) {
                val point = readPoint()
                lineTo(point.x, point.y)
            }
            close()
        }
    }
}

private fun DataInputStream.readPoint(): Offset {
    val x = readCount(360_000)
    val y = readCount(180_000)
    return Offset(x / 1000f, y / 1000f)
}

private fun DataInputStream.readCount(maximum: Int, minimum: Int = 0): Int =
    readInt().takeIf { it in minimum..maximum } ?: throw IOException("Invalid bundled map data")

private fun offlineRegions(assets: AssetManager): List<OfflineRegion> =
    listOf("offline_map/london.fpd", "offline_map/varna.fpd").mapNotNull { path ->
        try {
            DataInputStream(assets.open(path).buffered()).use { OfflineRegion(path, it.readDetailHeader()) }
        } catch (_: IOException) {
            null
        }
    }

private fun readOfflineDetail(assets: AssetManager, region: OfflineRegion, checkActive: () -> Unit): OfflineDetail =
    DataInputStream(assets.open(region.asset).buffered()).use { input ->
        if (input.readDetailHeader() != region.bounds) throw IOException("Unexpected bundled map bounds")
        val cells = List(input.readCount(512)) {
            val bounds = input.readDetailBounds()
            DetailCell(bounds, DetailLayer.entries.map { input.readDetailLayer(it.closed, checkActive) })
        }
        val roads = List(input.readCount(60_000)) {
            checkActive()
            val point = input.readDetailPoint()
            val length = input.readUnsignedShort()
            if (length !in 1..160) throw IOException("Invalid road name")
            BasemapPlace(point, 0, ByteArray(length).also(input::readFully).toString(Charsets.UTF_8))
        }
        if (input.read() != -1) throw IOException("Unexpected map detail data")
        OfflineDetail(cells, roads)
    }

private fun DataInputStream.readDetailHeader(): Rect {
    if (readInt() != 0x46504431) throw IOException("Unsupported map detail format")
    return readDetailBounds()
}

private fun DataInputStream.readDetailBounds(): Rect {
    val first = readDetailPoint()
    val last = readDetailPoint()
    if (first.x > last.x || first.y > last.y) throw IOException("Invalid map detail bounds")
    return Rect(first, last)
}

private fun DataInputStream.readDetailPoint(): Offset =
    Offset(readCount(36_000_000) / 100_000f, readCount(18_000_000) / 100_000f)

private fun DataInputStream.readDetailLayer(closed: Boolean, checkActive: () -> Unit) = Path().apply {
    repeat(readCount(70_000)) {
        checkActive()
        val count = readCount(100_000, minimum = if (closed) 3 else 2)
        val first = readDetailPoint()
        moveTo(first.x, first.y)
        repeat(count - 1) {
            val next = readDetailPoint()
            lineTo(next.x, next.y)
        }
        if (closed) close()
    }
}
