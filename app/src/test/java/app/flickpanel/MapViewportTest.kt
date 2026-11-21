package app.flickpanel

import androidx.compose.ui.geometry.Offset
import app.flickpanel.ui.MapViewport
import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewportTest {
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
