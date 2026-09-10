package voidmei.telemetry

import kotlin.test.*

class AttitudeGeometryTest {
    @Test fun levelHorizonIsHorizontal() {
        val attitude = AttitudeGeometry.fromIndicators(0.0, 0.0)!!
        assertEquals(-100.0 to 0.0, attitude.project(-100.0, 0.0, 2.0))
        assertEquals(100.0 to 0.0, attitude.project(100.0, 0.0, 2.0))
    }
    @Test fun pitchMovesHorizonAndLadderConsistently() {
        val attitude = AttitudeGeometry.fromIndicators(20.0, 0.0)!!
        assertEquals(0.0 to 40.0, attitude.project(0.0, 0.0, 2.0))
        assertEquals(0.0 to 0.0, attitude.project(0.0, 20.0, 2.0))
    }
    @Test fun quarterRollRotatesHorizonAndPitchDisplacementTogether() {
        val attitude = AttitudeGeometry.fromIndicators(10.0, 90.0)!!
        val right = attitude.project(100.0, 0.0, 2.0)
        assertEquals(20.0, right.first, 1e-10)
        assertEquals(-100.0, right.second, 1e-10)
    }
    @Test fun anglesWrapAndMissingValuesDoNotCreateFalseLevelFlight() {
        assertEquals(-90.0, AttitudeGeometry.fromIndicators(0.0, 270.0)!!.rollDeg)
        assertEquals(350.0, AttitudeGeometry.heading(-10.0))
        assertEquals(0.0, AttitudeGeometry.heading(720.0))
        assertNull(AttitudeGeometry.fromIndicators(null, 0.0))
        assertNull(AttitudeGeometry.fromIndicators(100.0, 0.0))
        assertNull(AttitudeGeometry.fromIndicators(0.0, Double.NaN))
        assertNull(AttitudeGeometry.heading(Double.POSITIVE_INFINITY))
    }
}
