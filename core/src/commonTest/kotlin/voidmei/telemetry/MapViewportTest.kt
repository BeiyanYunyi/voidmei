package voidmei.telemetry

import kotlin.test.*

class MapViewportTest {
    private fun bounds(w: Double, h: Double) = MapBounds(MapPoint(-1000.0, -2000.0),
        MapPoint(w - 1000, h - 2000), 1, null, null)

    @Test fun wideTallAndSquareMapsFitWithoutStretching() {
        assertEquals(MapViewport(0.0, 80.0, 320.0, 160.0), MapViewport.fit(bounds(2000.0, 1000.0), 320.0, 320.0))
        assertEquals(MapViewport(80.0, 0.0, 160.0, 320.0), MapViewport.fit(bounds(1000.0, 2000.0), 320.0, 320.0))
        assertEquals(MapViewport(0.0, 0.0, 320.0, 320.0), MapViewport.fit(bounds(1000.0, 1000.0), 320.0, 320.0))
        val wide = MapViewport.fit(bounds(2000.0, 1000.0), 320.0, 320.0)!!
        assertEquals(MapPoint(160.0, 160.0), wide.project(MapPoint(0.5, 0.5)))
        assertEquals(80.0, wide.project(MapPoint(0.25, 0.0)).x)
        assertEquals(80.0, wide.project(MapPoint(0.0, 0.5)).y - wide.top)
    }

    @Test fun invalidViewportOrMapDoesNotProduceCoordinates() {
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(MapViewport.fit(bounds(invalid, 1000.0), 320.0, 320.0))
            assertNull(MapViewport.fit(bounds(1000.0, 1000.0), invalid, 320.0))
        }
    }
}
