package voidmei.telemetry

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest

class MapTelemetryTest {
    private val info = """{"valid":true,"map_min":[-500,-1000],"map_max":[500,1000],"map_generation":9}"""
    @Test fun directionNormalizationPreservesSignsAndRejectsZeroOrMissing() {
        fun direction(x: Double, y: Double) = MapObject(null, null, null, null, MapPoint(x, y), null, null, null).unitDirection
        assertEquals(MapPoint(0.6, -0.8), direction(3.0, -4.0))
        assertNull(direction(0.0, 0.0))
        assertNull(direction(Double.NaN, 1.0))
        val huge = assertNotNull(direction(Double.MAX_VALUE, Double.MAX_VALUE))
        assertEquals(1.0, kotlin.math.hypot(huge.x, huge.y), 1e-12)
        assertEquals(MapPoint(1.0, 0.0), direction(Double.MIN_VALUE, 0.0))
        assertNull(MapTelemetryParser.objects("""[{"x":0.5,"y":0.5,"dx":1}]""").single().unitDirection)
    }
    @Test fun parsesReorderedObjectsSegmentsAndPlayerDistance() {
        val objects = MapTelemetryParser.objects("""[
            {"y":0.25,"icon":"Player","x":0.25,"type":"aircraft","color[]":[250,200,30]},
            {"x":0.55,"y":0.45,"color":"#174DFF"},
            {"type":"airfield","sx":0.1,"sy":0.2,"ex":0.2,"ey":0.3}
        ]""")
        val snapshot = MapSnapshot(MapTelemetryParser.info(info)!!, objects)
        assertEquals(9, snapshot.bounds.generation)
        assertEquals(0xFAC81E, objects[0].colorRgb)
        assertEquals(0x174DFF, objects[1].colorRgb)
        assertEquals(500.0, snapshot.distanceFromPlayerM(objects[1])!!, 1e-9)
        assertNull(snapshot.distanceFromPlayerM(objects[2]))
        assertEquals(MapPoint(0.1, 0.2), objects[2].start)
    }

    @Test fun absentOrInvalidCoordinatesNeverBecomeOrigin() {
        val objects = MapTelemetryParser.objects("""[{"icon":"Player","x":"0","y":0},{"x":-65535,"y":1},{"color[]":[999,0,0]}]""")
        assertTrue(objects.all { it.position == null })
        assertNull(objects.last().colorRgb)
        assertNull(MapTelemetryParser.info(info.replace("true", "false")))
        assertNull(MapTelemetryParser.info(info.replace("[500,1000]", "[-500,-1000]")))
        val player = MapObject(null, "Player", null, MapPoint(0.0, 0.0), null, null, null, null)
        assertNull(MapSnapshot(MapTelemetryParser.info(info)!!, listOf(player, player)).player)
    }

    @Test fun pollerClearsPreviousSnapshotAfterFailureAndRecovers() = runTest {
        var attempt = 0
        val transport = TelemetryTransport { path ->
            if (path == "/map_info.json") {
                attempt++
                if (attempt == 3) error("offline")
                info
            } else "[]"
        }
        val states = MapPoller(transport, 100).states().take(4).toList()
        assertIs<MapConnection.Connecting>(states[0])
        assertIs<MapConnection.Available>(states[1])
        assertIs<MapConnection.Unavailable>(states[2])
        assertIs<MapConnection.Available>(states[3])
    }

    @Test fun slowUpdateClearsSnapshotBeforeTimeoutAndRecovers() = runTest {
        var reads = 0
        val transport = TelemetryTransport { path ->
            if (path == "/map_info.json") {
                reads++
                if (reads == 3) delay(1500)
                info
            } else "[]"
        }
        val states = MapPoller(transport, 100).states().take(4).toList()
        assertIs<MapConnection.Available>(states[1])
        assertEquals(MapConnection.Unavailable("地图更新延迟"), states[2])
        assertIs<MapConnection.Available>(states[3])
        assertEquals(4, reads, "Delayed update must finish without starting a duplicate request")
    }

    @Test fun delayedRequestStillTimesOutAndCollectorFailureCancelsIt() = runTest {
        var closed = 0
        val transport = TelemetryTransport { try { awaitCancellation() } finally { closed++ } }
        val states = MapPoller(transport).states().take(3).toList()
        assertEquals(MapConnection.Unavailable("地图更新延迟"), states[1])
        assertEquals(MapConnection.Unavailable("地图请求超时"), states[2])
        assertEquals(1, closed)
        assertFailsWith<IllegalStateException> {
            MapPoller(transport).states().collect {
                if (it is MapConnection.Unavailable) error("collector failed")
            }
        }
        assertEquals(2, closed)
    }

    @Test fun cancellationStopsPendingRequest() = runTest {
        var closed = 0
        val transport = TelemetryTransport { try { awaitCancellation() } finally { closed++ } }
        val job = launch { MapPoller(transport).states().collect() }
        yield(); yield(); yield()
        job.cancelAndJoin()
        assertEquals(1, closed)
    }

    @Test fun generationChangeDuringObjectFetchDiscardsMixedSnapshot() = runTest {
        val requests = mutableListOf<String>()
        var metadataReads = 0
        val transport = TelemetryTransport { path ->
            requests += path
            if (path == "/map_info.json") {
                metadataReads++
                if (metadataReads == 1) info else info.replace(":9", ":10")
            } else "[]"
        }
        val states = MapPoller(transport, 100).states().take(3).toList()
        assertIs<MapConnection.Waiting>(states[1])
        assertEquals(10, assertIs<MapConnection.Available>(states[2]).snapshot.bounds.generation)
        assertEquals(listOf("/map_info.json", "/map_obj.json", "/map_info.json"), requests.take(3))
    }

    @Test fun invalidMapDoesNotFetchObjectsAndCollectorErrorsPropagate() = runTest {
        val requests = mutableListOf<String>()
        val transport = TelemetryTransport { path -> requests += path; """{"valid":false}""" }
        val states = MapPoller(transport).states().take(2).toList()
        assertIs<MapConnection.Waiting>(states[1])
        assertEquals(listOf("/map_info.json"), requests)
        assertFailsWith<IllegalStateException> {
            MapPoller(transport).states().collect { if (it is MapConnection.Waiting) error("collector failed") }
        }
    }
}
