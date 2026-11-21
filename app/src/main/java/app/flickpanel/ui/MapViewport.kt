package app.flickpanel.ui

import androidx.compose.ui.geometry.Offset
import app.flickpanel.ha.PersonLocation
import kotlin.math.cos

internal const val metresPerLatitudeDegree = 111_320f
internal const val maximumMapSpan = 180f

internal fun PersonLocation.mapPoint() = Offset((longitude + 180).toFloat(), (90 - latitude).toFloat())

internal data class MapViewport(val centre: Offset, val latitudeSpan: Float) {
    fun scale(height: Float): Offset {
        val vertical = height.coerceAtLeast(1f) / latitudeSpan
        val latitude = Math.toRadians((90f - centre.y).toDouble())
        return Offset(vertical * cos(latitude).toFloat().coerceAtLeast(.12f), vertical)
    }

    fun screenPoint(point: Offset, screenCentre: Offset, scale: Offset): Offset {
        val difference = point.x - centre.x
        val longitude = when {
            difference > 180f -> difference - 360f
            difference < -180f -> difference + 360f
            else -> difference
        }
        return screenCentre + Offset(longitude * scale.x, (point.y - centre.y) * scale.y)
    }

    fun transform(
        screenCentre: Offset, centroid: Offset, pan: Offset, zoom: Float, minimumSpan: Float,
    ): MapViewport {
        val height = screenCentre.y * 2f
        val before = scale(height)
        val anchor = centre + Offset((centroid.x - screenCentre.x) / before.x, (centroid.y - screenCentre.y) / before.y)
        val span = (latitudeSpan / zoom).coerceIn(minimumSpan, maximumMapSpan)
        val target = centroid + pan - screenCentre
        val latitude = (anchor.y - target.y * span / height.coerceAtLeast(1f)).coerceIn(0f, 180f)
        val next = MapViewport(Offset(centre.x, latitude), span)
        val longitude = anchor.x - target.x / next.scale(height).x
        return next.copy(centre = Offset(wrapMapLongitude(longitude), latitude))
    }
}

private fun wrapMapLongitude(value: Float): Float =
    if (value >= 0f && value < 360f) value else ((value % 360f) + 360f) % 360f
