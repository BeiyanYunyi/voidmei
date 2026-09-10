package voidmei.telemetry

import kotlin.test.*

class CalculationBoundaryTest {
    private fun telemetry() = TelemetryParser.parse(
        """{"valid":true,"TAS, km/h":360,"H, m":1000,"Vy, m/s":0,"thrust 1, kgs":100,"power 1, hp":100}""",
        """{"valid":true,"type":"test"}""")!!

    @Test fun overflowingCalculationsBecomeUnknownWithoutHidingValidReadings() {
        val base = telemetry()
        val huge = base.copy(tasKmh = 1e308, loadG = 1e308, rollDeg = 90.0,
            pitchDeg = 0.0, angleOfAttackDeg = 0.0,
            engines = listOf(base.engines.single().copy(powerHp = 1e308),
                base.engines.single().copy(index = 2, powerHp = 1e308)))
        val result = FlightCalculator().update(huge, 0)
        assertNull(result.energyHeightM)
        assertNull(result.estimatedTurnRadiusM)
        assertNull(result.estimatedTurnRateDegps)
        assertNull(result.totalPowerHp)
        assertNull(result.thrustPowerKw)
        assertNull(result.standardDynamicPressurePa)
        assertEquals(200.0, result.totalThrustKgf)
        assertNotNull(result.standardDensityKgM3)
        val invalid = FlightCalculator().update(base.copy(fuelKg = Double.POSITIVE_INFINITY,
            fuelCapacityKg = 100.0, altitudeM = Double.NaN), 0)
        assertNull(invalid.fuelPercent)
        assertNull(invalid.energyHeightM)
    }

    @Test fun wrappedBackwardTimestampCannotProduceAcceleration() {
        val calculator = FlightCalculator()
        calculator.update(telemetry(), Long.MAX_VALUE)
        assertNull(calculator.update(telemetry().copy(tasKmh = 720.0), Long.MIN_VALUE).accelerationMps2)
        assertEquals(10.0, calculator.update(telemetry().copy(tasKmh = 756.0), Long.MIN_VALUE + 1000).accelerationMps2)
    }

    @Test fun fuelWindowWorksAtNegativeClockOriginAndResetsAcrossOverflow() {
        val estimator = FuelEstimator()
        var estimate = FuelEstimate()
        for (second in 0..10) estimate = estimator.update(100.0 - second, Long.MIN_VALUE + second * 1000L)
        assertEquals(60.0, estimate.consumptionKgPerMinute)
        assertEquals(90.0, estimate.enduranceSeconds)
        assertEquals(FuelEstimate(), estimator.update(80.0, Long.MAX_VALUE))
    }

    @Test fun fuelRateAvoidsIntermediateOverflowButRejectsUnrepresentableResults() {
        val estimator = FuelEstimator()
        var estimate = FuelEstimate()
        for (second in 0..10) estimate = estimator.update(1e307 - second * 1e305, second * 1000L)
        assertTrue(assertNotNull(estimate.consumptionKgPerMinute).isFinite())
        assertTrue(assertNotNull(estimate.enduranceSeconds).isFinite())
        estimator.reset()
        for (second in 0..10) estimate = estimator.update(1e308 - second * 9e306, second * 1000L)
        assertNull(estimate.consumptionKgPerMinute)
        assertTrue(assertNotNull(estimate.enduranceSeconds).isFinite())
    }
}
