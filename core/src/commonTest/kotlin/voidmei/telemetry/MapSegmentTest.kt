package voidmei.telemetry

import kotlin.test.*

class MapSegmentTest {
    @Test fun crossingSegmentsKeepTheVisiblePortionAndDirection() {
        val segment = MapSegment(MapPoint(-1.0, .5), MapPoint(2.0, .5))
        assertEquals(MapSegment(MapPoint(0.0, .5), MapPoint(1.0, .5)), segment.clipped())
        assertEquals(MapSegment(MapPoint(1.0, .5), MapPoint(0.0, .5)), MapSegment(segment.end, segment.start).clipped())
        assertEquals(MapSegment(MapPoint(0.0, 0.0), MapPoint(1.0, 1.0)),
            MapSegment(MapPoint(-1.0, -1.0), MapPoint(2.0, 2.0)).clipped())
        val inside = MapSegment(MapPoint(.2, .3), MapPoint(.8, .9))
        val clipped = assertNotNull(inside.clipped())
        assertEquals(inside.start, clipped.start)
        assertEquals(inside.end.x, clipped.end.x, 1e-12)
        assertEquals(inside.end.y, clipped.end.y, 1e-12)
    }

    @Test fun outsideParallelInvalidAndOverflowingSegmentsDoNotDraw() {
        for (segment in listOf(
            MapSegment(MapPoint(-1.0, -.1), MapPoint(2.0, -.1)),
            MapSegment(MapPoint(2.0, 2.0), MapPoint(2.0, 2.0)),
            MapSegment(MapPoint(Double.NaN, .5), MapPoint(.5, .5)),
            MapSegment(MapPoint(-Double.MAX_VALUE, .5), MapPoint(Double.MAX_VALUE, .5)),
        )) assertNull(segment.clipped())
        val point = MapSegment(MapPoint(.5, .5), MapPoint(.5, .5))
        assertEquals(point, point.clipped())
    }
}
