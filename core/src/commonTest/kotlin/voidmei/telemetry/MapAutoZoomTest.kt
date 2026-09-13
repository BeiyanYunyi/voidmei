package voidmei.telemetry

import kotlin.test.*

class MapAutoZoomTest {
    private val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(2000.0, 1000.0), 1, null, null)
    private fun point(x: Double, y: Double) = MapObject(null, null, null, MapPoint(x, y), null, null, null, null)

    @Test fun fitsOffCentreBattlefieldWithMarginsAndPreservesMetreScale() {
        val snapshot = MapSnapshot(bounds, listOf(point(.1, .2), point(.3, .4)))
        for ((w, h) in listOf(320.0 to 320.0, 600.0 to 200.0, 200.0 to 600.0)) {
            val view = MapViewport.fitObjects(snapshot, w, h)!!
            assertEquals(view.width / bounds.widthM, view.height / bounds.heightM, 1e-9)
            assertEquals(w / 2, view.project(MapPoint(.2, .3)).x, 1e-9)
            assertEquals(h / 2, view.project(MapPoint(.2, .3)).y, 1e-9)
            snapshot.objects.forEach {
                val pixel = view.project(it.position!!)
                assertTrue(pixel.x >= w * .08 - 1e-9 && pixel.x <= w * .92 + 1e-9)
                assertTrue(pixel.y >= h * .08 - 1e-9 && pixel.y <= h * .92 + 1e-9)
                assertEquals(it, MapHitTest.nearest(snapshot, view, pixel, 5.0))
            }
        }
    }

    @Test fun includesClippedLineEndpointsAndIgnoresInvisibleGeometry() {
        val line = MapObject(null, null, null, null, null, MapPoint(-.2, .4), MapPoint(.6, .4), null)
        val snapshot = MapSnapshot(bounds, listOf(line, point(.2, .5), point(4.0, 4.0), point(Double.NaN, .2)))
        val view = MapViewport.fitObjects(snapshot, 320.0, 320.0)!!
        assertEquals(25.6, view.project(MapPoint(0.0, .4)).x, 1e-9)
        assertEquals(294.4, view.project(MapPoint(.6, .4)).x, 1e-9)
    }

    @Test fun emptyFallsBackAndCoincidentPointsHaveFiniteLimitedZoom() {
        val empty = MapSnapshot(bounds, listOf(point(2.0, 2.0)))
        assertEquals(MapViewport.fit(bounds, 320.0, 320.0), MapViewport.fitObjects(empty, 320.0, 320.0))
        val snapshot = MapSnapshot(bounds, listOf(point(0.0, 0.0), point(0.0, 0.0)))
        val view = MapViewport.fitObjects(snapshot, 320.0, 320.0)!!
        assertEquals(6400.0, view.width)
        assertEquals(MapPoint(160.0, 160.0), view.project(MapPoint(0.0, 0.0)))
        assertNull(MapViewport.fitObjects(snapshot, 0.0, 320.0))
    }

    @Test fun refitsWhenObjectsMoveOrDisappear() {
        val near = MapSnapshot(bounds, listOf(point(.4, .4), point(.5, .5)))
        val far = near.copy(objects = near.objects + point(.9, .9))
        assertTrue(MapViewport.fitObjects(near, 320.0, 320.0)!!.width >
            MapViewport.fitObjects(far, 320.0, 320.0)!!.width)
    }
}
