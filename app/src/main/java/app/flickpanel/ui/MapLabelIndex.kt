package app.flickpanel.ui

import androidx.compose.ui.geometry.Rect

internal class MapLabelIndex(places: List<BasemapPlace>) {
    private val byLatitude = places.withIndex().sortedBy { it.value.point.y }

    fun within(window: Rect): List<BasemapPlace> {
        val start = firstAtOrBelow(window.top)
        val end = firstAtOrBelow(window.bottom)
        if (start >= end) return emptyList()

        val wrap = when {
            window.left < 0f -> -360f
            window.right > 360f -> 360f
            else -> 0f
        }
        fun visible(longitude: Float) = longitude >= window.left && longitude < window.right
        return byLatitude.subList(start, end)
            .filter { (_, place) ->
                val x = place.point.x
                visible(x) || wrap != 0f && visible(x + wrap)
            }
            // Preserve the asset's priority order for label collisions.
            .sortedBy { it.index }
            .map { it.value }
    }

    private fun firstAtOrBelow(latitude: Float): Int {
        var low = 0
        var high = byLatitude.size
        while (low < high) {
            val mid = (low + high) / 2
            if (byLatitude[mid].value.point.y < latitude) low = mid + 1 else high = mid
        }
        return low
    }
}
