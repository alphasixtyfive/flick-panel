package app.flickpanel.ui

import android.content.res.AssetManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import java.io.DataInputStream
import java.io.IOException

internal data class BasemapPlace(val point: Offset, val rank: Int, val name: String)

internal data class OfflineBasemap(val countries: Path, val lakes: Path, val places: List<BasemapPlace>)

internal fun loadOfflineBasemap(assets: AssetManager): OfflineBasemap =
    DataInputStream(assets.open("offline_map/world.fpm").buffered()).use { input ->
        if (input.readInt() != 0x46504d31) throw IOException("Unsupported bundled map format")
        val countries = input.readLayer()
        val lakes = input.readLayer()
        val places = List(input.readCount(2_000)) {
            val point = input.readPoint()
            val rank = input.readUnsignedByte()
            val length = input.readUnsignedShort()
            if (length !in 1..200) throw IOException("Invalid map label")
            val nameBytes = ByteArray(length).also(input::readFully)
            BasemapPlace(point, rank, nameBytes.toString(Charsets.UTF_8))
        }
        if (input.read() != -1) throw IOException("Unexpected map data")
        OfflineBasemap(countries, lakes, places)
    }

private fun DataInputStream.readLayer(): Path {
    var totalPoints = 0
    return Path().apply {
        fillType = PathFillType.EvenOdd
        repeat(readCount(5_000)) {
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
