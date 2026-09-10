package voidmei.recording

import kotlin.test.*

class FlightRecordPlotsTest {
    private val header = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n"
    @Test fun boundsPlotSizeAndKeepsFirstLastAndExtrema() {
        val text = header + (0..10000).joinToString("\n") { i ->
            val value = when (i) { 4321 -> 1000; 4567 -> -10; else -> 300 }
            "$i,${100000 + i},$i,plane,$value"
        }
        val analysis = FlightRecordPlots.analyze(text, buckets = 10)
        val points = analysis.plots.getValue("ias_kmh")
        assertTrue(points.size <= 40)
        assertEquals(0L, points.first().elapsedMs)
        assertEquals(10000L, points.last().elapsedMs)
        assertTrue(points.any { it.value == 1000.0 })
        assertTrue(points.any { it.value == -10.0 })
        assertTrue(points.zipWithNext().all { (a, b) -> b.elapsedMs >= a.elapsedMs })
        assertFalse(points.first().connectFromPrevious)
        assertTrue(points.drop(1).all { it.connectFromPrevious })
    }

    @Test fun missingDataAndLongGapsDoNotBecomeContinuousLines() {
        val text = header + "0,1000,0,plane,300\n1,1100,100,plane,\n2,1200,200,plane,350\n3,9000,8000,plane,400"
        val points = FlightRecordPlots.analyze(text, buckets = 2).plots.getValue("ias_kmh")
        assertTrue(points.none { it.connectFromPrevious })
        assertEquals(listOf(300.0, 350.0, 400.0), points.map { it.value })
    }

    @Test fun equalTimestampsAndMissingMetricsRemainWellDefined() {
        val text = header + "0,1000,5,plane,300\n1,1000,5,plane,500\n2,1000,5,plane,400"
        val result = FlightRecordPlots.analyze(text)
        assertEquals(0L, result.summary.spanMs)
        assertEquals(listOf(300.0, 500.0, 400.0), result.plots.getValue("ias_kmh").map { it.value })
        assertNull(result.plots["altitude_m"])
        assertFails { FlightRecordPlots.analyze(text, buckets = 0) }
        assertFails { FlightRecordPlots.analyze(text, buckets = 1001) }
    }
}
