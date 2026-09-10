package voidmei.recording

import kotlin.test.*

class AngularRecordPlotsTest {
    private fun csv(values: List<Int>) = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,heading_deg,roll_deg,ias_kmh\n" +
        values.mapIndexed { i, v -> "$i,${1000 + i * 100},${i * 100},test,$v,$v,$v" }.joinToString("\n")

    @Test fun wrapBoundariesBreakBothCyclicAngleCurvesButNotOrdinaryMetrics() {
        val text = csv(listOf(358, 359, 0, 1, 2))
        val plots = FlightRecordPlots.analyze(text).plots
        for (field in listOf("heading_deg", "roll_deg")) {
            val points = plots.getValue(field)
            assertEquals(listOf(358.0, 359.0, 0.0, 1.0, 2.0), points.map { it.value })
            assertFalse(points[2].connectFromPrevious)
            assertTrue(points[4].connectFromPrevious)
        }
        assertTrue(plots.getValue("ias_kmh").drop(1).all { it.connectFromPrevious })
        assertEquals(359.0, RecordedReplay(text).frame(1).values["heading_deg"])
    }

    @Test fun signedRollWrapAndWrapHiddenInsideABucketCannotBecomeContinuous() {
        val signed = FlightRecordPlots.analyze(csv(listOf(179, -179, -178))).plots.getValue("roll_deg")
        assertFalse(signed[1].connectFromPrevious)
        val binned = FlightRecordPlots.analyze(csv(listOf(350, 359, 0, 10)), buckets = 1).plots.getValue("heading_deg")
        assertTrue(binned.none { it.connectFromPrevious })
        assertTrue(binned.any { it.value == 359.0 })
        assertTrue(binned.any { it.value == 0.0 })
    }

    @Test fun samplingCannotJoinSelectedPointsMoreThanHalfATurnApart() {
        val points = FlightRecordPlots.analyze(csv(listOf(0, 90, 180, 270)), buckets = 1).plots.getValue("heading_deg")
        assertEquals(listOf(0.0, 270.0), points.map { it.value })
        assertFalse(points.last().connectFromPrevious)
        val half = FlightRecordPlots.analyze(csv(listOf(0, 180))).plots.getValue("heading_deg")
        assertTrue(half.last().connectFromPrevious)
    }
}
