package voidmei.telemetry

import kotlin.test.*

class CockpitUnitEstimatorTest {
    private val base = TelemetryParser.parse("""{"valid":true,"H, m":1000}""",
        """{"valid":true,"type":"test","altitude_10k":4000}""")!!

    private fun sample(i: Int, scale: Double = 1.0, direction: Int = 1) = base.copy(
        altitudeM = 1000.0 + i * direction,
        altimeterRaw = 4000.0 + i * direction * scale)

    @Test fun waitsForContinuousMotionAndRecognizesBothScalesInBothDirections() {
        assertEquals(4000.0, base.altimeterRaw)
        for ((scale, expected) in listOf(1.0 to CockpitAltitudeUnit.METRES, 3.28084 to CockpitAltitudeUnit.FEET)) {
            for (direction in listOf(-1, 1)) {
                val estimator = CockpitUnitEstimator()
                for (i in 0..9) assertNull(estimator.update(sample(i, scale, direction), i * 1000L))
                assertEquals(expected, estimator.update(sample(10, scale, direction), 10000))
                for (i in 11..45) assertEquals(expected, estimator.update(sample(10, scale, direction), i * 1000L))
            }
        }
        for (scale in listOf(0.0, 2.0, -1.0)) {
            val estimator = CockpitUnitEstimator()
            for (i in 0..20) assertNull(estimator.update(sample(i, scale), i * 1000L))
        }
        val stationary = CockpitUnitEstimator()
        for (i in 0..40) assertNull(stationary.update(base, i * 1000L))
    }

    @Test fun invalidSamplesAircraftChangesAndTimeDiscontinuitiesClearConfirmation() {
        for ((next, time) in listOf(
            sample(11).copy(aircraft = "other") to 11000L,
            sample(11).copy(aircraft = null) to 11000L,
            sample(11).copy(altimeterRaw = null) to 11000L,
            sample(11).copy(altimeterRaw = Double.NaN) to 11000L,
            sample(11).copy(altitudeM = Double.POSITIVE_INFINITY) to 11000L,
            sample(11) to 13000L, sample(11) to 10000L, sample(11) to 9000L,
        )) {
            val calculator = FlightCalculator()
            for (i in 0..10) calculator.update(sample(i), i * 1000L)
            assertNull(calculator.update(next, time).cockpitAltitudeUnit)
        }
        val calculator = FlightCalculator()
        for (i in 0..10) calculator.update(sample(i), i * 1000L)
        calculator.reset()
        assertNull(calculator.update(sample(11), 11000).cockpitAltitudeUnit)
    }
}
