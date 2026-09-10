package voidmei.telemetry

import kotlin.math.*
import kotlin.test.*
import voidmei.physics.StandardAtmosphere

class FlightCalculationTest {
    private fun flight(fields: String = "") = TelemetryParser.parse(
        """{"valid":true,"TAS, km/h":360,"H, m":1000,"Vy, m/s":0$fields}""",
        """{"valid":true,"type":"test","aviahorizon_roll":0,"aviahorizon_pitch":0}""",
    )!!

    @Test fun standardAtmosphereMatchesPublishedLayerBoundaries() {
        // NASA/TM-2005-213659, table 1; geopotential altitude in metres.
        val expected = listOf(
            Triple(0.0, 288.15, 101325.0), Triple(11000.0, 216.65, 22632.06),
            Triple(20000.0, 216.65, 5474.89), Triple(32000.0, 228.65, 868.02),
        )
        expected.forEach { (altitude, temperature, pressure) ->
            val air = StandardAtmosphere.atGeopotentialAltitude(altitude)!!
            assertEquals(temperature, air.temperatureK, 1e-8)
            assertEquals(pressure, air.pressurePa, 0.02)
            assertEquals(altitude, StandardAtmosphere.geopotentialAltitudeAtPressure(air.pressurePa)!!, 1e-6)
        }
        assertEquals(1.225, StandardAtmosphere.atGeopotentialAltitude(0.0)!!.densityKgM3, 1e-6)
        assertEquals(340.294, StandardAtmosphere.atGeopotentialAltitude(0.0)!!.speedOfSoundMps, 0.001)
    }

    @Test fun atmosphereIsContinuousMonotonicAndRejectsOutOfRange() {
        var lastPressure = Double.POSITIVE_INFINITY
        for (altitude in -4000..32000 step 100) {
            val air = StandardAtmosphere.atGeopotentialAltitude(altitude.toDouble())!!
            assertTrue(air.pressurePa < lastPressure)
            assertTrue(air.densityKgM3 > 0)
            lastPressure = air.pressurePa
        }
        for (boundary in listOf(11000.0, 20000.0)) {
            val below = StandardAtmosphere.atGeopotentialAltitude(boundary - 0.001)!!
            val above = StandardAtmosphere.atGeopotentialAltitude(boundary + 0.001)!!
            assertEquals(below.pressurePa, above.pressurePa, 0.01)
            assertEquals(below.temperatureK, above.temperatureK, 0.00001)
        }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -4001.0, 32001.0))
            assertNull(StandardAtmosphere.atGeopotentialAltitude(invalid))
        assertNull(StandardAtmosphere.geopotentialAltitudeAtPressure(0.0))
        assertNull(StandardAtmosphere.geopotentialAltitudeAtPressure(Double.NaN))
        assertTrue(StandardAtmosphere.atGeometricAltitude(11000.0)!!.pressurePa > StandardAtmosphere.atGeopotentialAltitude(11000.0)!!.pressurePa)
    }

    @Test fun coordinatedTurnAndStraightFlightDoNotRetainOldRadius() {
        val calculator = FlightCalculator()
        val straight = flight(""", "Ny":1,"AoA, deg":0""")
        val banked = straight.copy(loadG = 2.0, rollDeg = 60.0)
        val turning = calculator.update(banked, 0)
        val centripetal = FlightCalculator.G * sqrt(3.0)
        assertEquals(10000 / centripetal, turning.estimatedTurnRadiusM!!, 1e-8)
        assertEquals(centripetal / 100 * 180 / PI, turning.estimatedTurnRateDegps!!, 1e-8)
        val level = calculator.update(straight, 100)
        assertNull(level.estimatedTurnRadiusM)
        assertEquals(0.0, level.estimatedTurnRateDegps)
        assertNull(calculator.update(banked.copy(rollDeg = null), 200).estimatedTurnRadiusM)
        assertNull(calculator.update(banked.copy(tasKmh = 0.0), 300).estimatedTurnRateDegps)
    }

    @Test fun sepExactlyMatchesKineticEnergyDifferenceAtDifferentIntervals() {
        for (interval in listOf(100L, 500L, 1000L)) {
            val calculator = FlightCalculator()
            val initial = flight()
            calculator.update(initial, 0)
            val final = initial.copy(tasKmh = 396.0, verticalSpeedMps = 5.0)
            val metrics = calculator.update(final, interval)
            val expected = 5 + (110.0.pow(2) - 100.0.pow(2)) / (2 * FlightCalculator.G * interval / 1000)
            assertEquals(expected, metrics.specificExcessPowerMps!!, 1e-8)
            assertNull(calculator.update(final, interval).specificExcessPowerMps)
        }
    }

    @Test fun fuelUsesElapsedTimeAndResetsAfterRefuellingMissingValuesAndGaps() {
        val estimator = FuelEstimator()
        for (second in 0..9) assertNull(estimator.update(100.0 - second, second * 1000L).enduranceSeconds)
        val estimate = estimator.update(90.0, 10000)
        assertEquals(60.0, estimate.consumptionKgPerMinute)
        assertEquals(90.0, estimate.enduranceSeconds)
        assertNull(estimator.update(100.0, 11000).enduranceSeconds)
        assertNull(estimator.update(null, 12000).enduranceSeconds)
        for (second in 13..23) estimator.update(100.0 - second, second * 1000L)
        assertNull(estimator.update(70.0, 30000).enduranceSeconds)
        assertNull(estimator.update(70.0, 30000).enduranceSeconds)
    }

    @Test fun fuelAverageHandlesIrregularSamplesAndStopsEstimatingWhenConsumptionStops() {
        val estimator = FuelEstimator()
        for (timeMs in 0L..12000L step 1500) estimator.update(100.0 - timeMs / 1000.0, timeMs)
        val estimate = estimator.update(87.0, 13000)
        assertEquals(60.0, estimate.consumptionKgPerMinute)
        for (second in 14..45) estimator.update(87.0, second * 1000L)
        val stopped = estimator.update(87.0, 46000)
        assertEquals(0.0, stopped.consumptionKgPerMinute)
        assertNull(stopped.enduranceSeconds)
    }

    @Test fun fuelHistoryResetsOnAircraftAndCapacityChanges() {
        val calculator = FlightCalculator()
        val base = flight(""", "Mfuel, kg":100,"Mfuel0, kg":200""")
        for (second in 0..10) calculator.update(base.copy(fuelKg = 100.0 - second), second * 1000L)
        assertNull(calculator.update(base.copy(aircraft = "other", fuelKg = 89.0), 11000).fuelEnduranceSeconds)
        for (second in 12..23) calculator.update(base.copy(fuelKg = 100.0 - second), second * 1000L)
        assertNull(calculator.update(base.copy(fuelKg = 76.0, fuelCapacityKg = 300.0), 24000).fuelEnduranceSeconds)
        calculator.reset()
        assertNull(calculator.update(base, 25000).fuelEnduranceSeconds)
    }

    @Test fun engineTotalsRejectPartialDataAndUseSIThrustPower() {
        val t = flight(""", "power 1, hp":100,"power 2, hp":200,"thrust 1, kgs":300,"thrust 2, kgs":400""")
        val calculator = FlightCalculator()
        val metrics = calculator.update(t, 0)
        assertEquals(300.0, metrics.totalPowerHp)
        assertEquals(700.0, metrics.totalThrustKgf)
        assertEquals(700 * FlightCalculator.G * 100 / 1000, metrics.thrustPowerKw!!, 1e-8)
        val partial = calculator.update(t.copy(engines = t.engines + t.engines[0].copy(index = 3, powerHp = null, thrustKgf = null)), 100)
        assertNull(partial.totalPowerHp)
        assertNull(partial.totalThrustKgf)
        assertNull(partial.thrustPowerKw)
        assertNull(calculator.update(t.copy(engines = emptyList()), 200).totalPowerHp)
    }
}
