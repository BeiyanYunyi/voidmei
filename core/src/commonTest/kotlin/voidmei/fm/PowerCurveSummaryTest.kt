package voidmei.fm

import kotlin.test.*

class PowerCurveSummaryTest {
    @Test fun modelGeneratedCurvesKeepFeaturesOnActualSamplesAcrossFlightConditions() {
        // Existing synthetic stages already have pointwise legacy-Java reference coverage.
        for ((stageIndex, stage) in LegacyPistonReference.stages.withIndex()) {
            for (wep in listOf(false, true)) for (speed in listOf(0.0, 450.0)) {
                val points = PistonPowerModel.curve(listOf(stage), wep = wep, speedKmh = speed,
                    equivalentAirspeed = speed > 0, stepM = 25)
                val summary = PowerCurveSummary.from(points)
                val context = "stage=$stageIndex wep=$wep speed=$speed"
                summary.peak?.let { assertTrue(it in points, context) }
                for (feature in summary.extrema) {
                    assertTrue(feature.point in points, context)
                    assertEquals(0, feature.point.stageIndex, context)
                    val index = points.indexOf(feature.point)
                    assertTrue(index >= 4 && index <= points.lastIndex - 4, context)
                    assertTrue(points.subList(index - 4, index + 5).all { it != null }, context)
                }
                for (kind in PowerExtremumKind.entries) {
                    val sameKind = summary.extrema.filter { it.kind == kind }
                    assertTrue(sameKind.zipWithNext().all { (a, b) -> b.point.altitudeM - a.point.altitudeM >= 300 }, context)
                }
            }
        }
        val stage = CompressorStage(3000.0, 1400.0, 1200.0)
        val points = PistonPowerModel.curve(listOf(stage), stepM = 25)
        val summary = PowerCurveSummary.from(points)
        assertEquals(3000.0, summary.peak?.altitudeM)
        assertTrue(summary.extrema.any { it.kind == PowerExtremumKind.PEAK && it.point.altitudeM == 3000.0 })
    }
    @Test fun slopeKinksAreSeparatedFromExtremaAndNeverBridgeGaps() {
        val points = (0..80).map { index ->
            PistonPowerPoint(index * 25.0, 1000 + minOf(index, 60) * 0.5 + maxOf(index - 60, 0) * 12.5, 0)
        }
        assertEquals(listOf(PowerExtremum(points[60], PowerExtremumKind.KINK)), PowerCurveSummary.from(points).extrema)
        val missing = points.toMutableList<PistonPowerPoint?>().apply { this[60] = null }
        assertTrue(PowerCurveSummary.from(missing).extrema.isEmpty())
        val linear = points.map { it.copy(powerHp = 1000 + it.altitudeM * 0.02) }
        assertTrue(PowerCurveSummary.from(linear).extrema.isEmpty())
    }
    @Test fun sampledPeaksAndValleysMatchLegacyNeighborhoodAndNoiseThreshold() {
        val points = (0..40).map { index ->
            val altitude = index * 25.0
            val power = when {
                index <= 10 -> 1000.0 + index * 10
                index <= 25 -> 1100.0 - (index - 10) * 10
                else -> 950.0 + (index - 25) * 10
            }
            PistonPowerPoint(altitude, power, 0)
        }
        assertEquals(listOf(PowerExtremum(points[10], PowerExtremumKind.PEAK),
            PowerExtremum(points[25], PowerExtremumKind.VALLEY)), PowerCurveSummary.from(points).extrema)
        val broken = points.toMutableList<PistonPowerPoint?>().apply { this[10] = null; this[25] = null }
        assertTrue(PowerCurveSummary.from(broken).extrema.isEmpty())
        val noise = points.map { it.copy(powerHp = 1000 + (it.powerHp - 1000) * 0.001) }
        assertTrue(PowerCurveSummary.from(noise).extrema.isEmpty())
        assertTrue(PowerCurveSummary.from(points.reversed()).extrema.isEmpty())
    }
    @Test fun knownPeakAndTransitionsDoNotBridgeMissingOrInvalidSamples() {
        val firstPeak = PistonPowerPoint(100.0, 1200.0, 0)
        val summary = PowerCurveSummary.from(listOf(PistonPowerPoint(0.0, 1000.0, 0), firstPeak,
            PistonPowerPoint(200.0, 1200.0, 1), null, PistonPowerPoint(400.0, 1100.0, 2),
            PistonPowerPoint(500.0, Double.NaN, 3), PistonPowerPoint(600.0, 900.0, 4)))
        assertEquals(firstPeak, summary.peak)
        assertEquals(listOf(PowerStageTransition(100.0, 200.0, 0, 1)), summary.transitions)
        assertNull(PowerCurveSummary.from(listOf(null)).peak)
    }
}
