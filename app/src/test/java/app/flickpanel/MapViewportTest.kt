package app.flickpanel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.flickpanel.ui.BasemapPlace
import app.flickpanel.ui.MapLabelIndex
import app.flickpanel.ui.MapViewport
import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewportTest {
    @Test
    fun labelIndexKeepsVisibleLabelsInSourcePriorityOrder() {
        val index = MapLabelIndex(listOf(
            BasemapPlace(Offset(10f, 21f), 0, "Main road"),
            BasemapPlace(Offset(10f, 18f), 0, "Too far north"),
            BasemapPlace(Offset(11f, 20f), 0, "Side street"),
            BasemapPlace(Offset(16f, 20f), 0, "Off to the east"),
        ))
        assertEquals(listOf("Main road", "Side street"), index.within(Rect(9f, 20f, 13f, 22f)).map { it.name })
        assertEquals(emptyList<String>(), index.within(Rect(9f, 30f, 13f, 32f)).map { it.name })
    }

    @Test
    fun labelIndexIncludesRoadsAcrossTheDateline() {
        val index = MapLabelIndex(listOf(
            BasemapPlace(Offset(359.99f, 90f), 0, "West"),
            BasemapPlace(Offset(.01f, 90f), 0, "East"),
            BasemapPlace(Offset(.1f, 90f), 0, "Outside"),
        ))
        for (window in listOf(Rect(-.03f, 89f, .03f, 91f), Rect(359.97f, 89f, 360.03f, 91f))) {
            assertEquals(listOf("West", "East"), index.within(window).map { it.name })
        }
    }

    @Test
    fun pinchKeepsTheLocationUnderTheFingersStable() {
        val before = MapViewport(Offset(179.9f, 38.5f), .08f)
        val anchor = Offset(179.91f, 38.51f)
        val middle = Offset(600f, 400f)
        val centroid = before.screenPoint(anchor, middle, before.scale(800f))
        val pan = Offset(60f, -40f)
        val after = before.transform(middle, centroid, pan, zoom = 2f, minimumSpan = .006f)
        val target = after.screenPoint(anchor, middle, after.scale(800f))
        assertEquals(centroid.x + pan.x, target.x, .8f)
        assertEquals(centroid.y + pan.y, target.y, .8f)
        assertEquals(.04f, after.latitudeSpan, .0001f)
    }

    @Test
    fun nearestLongitudeWrapsAcrossTheDatelineAndZoomHasALimit() {
        val map = MapViewport(Offset(359.98f, 90f), .2f)
        val middle = Offset(300f, 400f)
        val scale = map.scale(800f)
        val east = map.screenPoint(Offset(.02f, 90f), middle, scale)
        assertEquals(460f, east.x, .4f)
        val close = map.transform(middle, middle, Offset.Zero, zoom = 100f, minimumSpan = .006f)
        assertEquals(.006f, close.latitudeSpan, .0001f)
    }
}
