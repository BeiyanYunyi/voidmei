package voidmei.telemetry

import kotlin.test.*

class MapHitTestTest {
    @Test fun selectionUsesFittedViewportAndRejectsPaddingAndOverlaps() {
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(2000.0, 1000.0), 1, null, null)
        val objects = MapTelemetryParser.objects("""[{"type":"aircraft","x":0.5,"y":0.25}]""")
        val snapshot = MapSnapshot(bounds, objects)
        val viewport = MapViewport.fit(bounds, 320.0, 320.0)!!
        assertEquals(objects.single(), MapHitTest.nearest(snapshot, viewport, MapPoint(160.0, 120.0), 12.0))
        assertNull(MapHitTest.nearest(snapshot, viewport, MapPoint(160.0, 20.0), 200.0))
        assertNull(MapHitTest.nearest(snapshot, viewport, MapPoint(180.0, 120.0), 12.0))
        assertNull(MapHitTest.nearest(snapshot.copy(objects = objects + objects), viewport, MapPoint(160.0, 120.0), 12.0))
        assertNull(MapHitTest.nearest(snapshot, viewport, MapPoint(Double.NaN, 120.0), 12.0))
    }
}
