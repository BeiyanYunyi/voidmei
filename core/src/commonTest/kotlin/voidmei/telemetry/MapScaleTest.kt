package voidmei.telemetry

import kotlin.test.*

class MapScaleTest {
    private fun bounds(width: Double) = MapBounds(MapPoint(0.0, 0.0), MapPoint(width, 1000.0), 1, null, null)
    @Test fun selectsReadableDistancesAndFitsTheMap() {
        for ((width, metres) in listOf(1000.0 to 200.0, 2000.0 to 500.0, 10000.0 to 2000.0, 40000.0 to 10000.0)) {
            val scale = MapScale.fromBounds(bounds(width))!!
            assertEquals(metres, scale.metres)
            assertEquals(metres / width, scale.widthFraction)
            val viewport = MapViewport.fit(bounds(width), 320.0, 320.0)!!
            assertEquals(metres * viewport.width / width, viewport.width * scale.widthFraction, 1e-10)
        }
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(MapScale.fromBounds(bounds(invalid)))
    }
}
