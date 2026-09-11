package voidmei.telemetry

import kotlin.math.abs
import kotlin.test.*

class SepSamplingTest {
    private fun flight(speed: Double, climb: Double = 0.0) = TelemetryParser.parse(
        """{"valid":true,"TAS, km/h":$speed,"Vy, m/s":$climb}""",
        """{"valid":true,"type":"test"}""",
    )!!

    @Test fun repeatedQuantizedSpeedsDoNotAlternateBetweenClimbAndAccelerationSpikes() {
        for (interval in listOf(10L, 50L, 80L, 100L, 125L, 200L)) {
            val calculator = FlightCalculator()
            val readings = mutableListOf<Double>()
            for (time in 0L..4000L step interval) {
                // API speed advances by one km/h every 200 ms; intervening polls repeat it.
                val speed = 360.0 + time / 200
                val value = calculator.update(flight(speed, 1.0), time).specificExcessPowerMps
                if (time >= 1000) {
                    val expected = 1.0 + (speed / 3.6) * (1 / 0.2 / 3.6) / FlightCalculator.G
                    assertTrue(abs(assertNotNull(value) - expected) < 2.0, "interval=$interval time=$time SEP=$value expected=$expected")
                    readings += value
                }
            }
            assertTrue(readings.max() - readings.min() < 3.0)
        }
    }

    @Test fun quantizedSpeedRemainsStableWithJitterAroundEightyMillisecondPolling() {
        for (direction in listOf(-1, 1)) {
            val calculator = FlightCalculator()
            var time = 0L
            var index = 0
            val jitter = listOf(71L, 89L, 75L, 85L, 110L, 52L)
            val readings = mutableListOf<Double>()
            while (time <= 5000) {
                val speed = 360.0 + direction * (time / 200)
                val sep = calculator.update(flight(speed, 1.0), time).specificExcessPowerMps
                if (time >= 1000) {
                    val expected = 1.0 + direction * (speed / 3.6) * (1 / 0.2 / 3.6) / FlightCalculator.G
                    assertTrue(abs(assertNotNull(sep) - expected) < 2.0,
                        "direction=$direction time=$time SEP=$sep expected=$expected")
                    readings += sep
                }
                time += jitter[index++ % jitter.size]
            }
            assertTrue(readings.all { if (direction > 0) it > 10 else it < -10 })
        }
    }

    @Test fun irregularPollingPreservesDecelerationAndLongIntervalsUseElapsedTime() {
        val calculator = FlightCalculator()
        for (time in listOf(0L, 73L, 181L, 300L, 507L, 710L, 1000L)) {
            // Linear loss of energy height: -10 m/s, independent of polling cadence.
            val speed = kotlin.math.sqrt(10000.0 - 2 * FlightCalculator.G * 10 * time / 1000.0) * 3.6
            val result = calculator.update(flight(speed, 2.0), time)
            if (time > 0) assertEquals(-8.0, assertNotNull(result.specificExcessPowerMps), 1e-8)
        }
        calculator.reset()
        calculator.update(flight(360.0), 0)
        val slowPoll = calculator.update(flight(396.0), 1500)
        assertEquals(10.0 / 1.5, assertNotNull(slowPoll.accelerationMps2), 1e-8)
        assertEquals((110.0 * 110 - 100.0 * 100) / (2 * FlightCalculator.G * 1.5),
            assertNotNull(slowPoll.specificExcessPowerMps), 1e-8)
    }

    @Test fun steadySpeedSettlesToClimbAndDiscontinuitiesDiscardHistory() {
        val calculator = FlightCalculator()
        for (time in 0L..1000L step 100) calculator.update(flight(360.0 + time / 100), time)
        for (time in 1100L..2200L step 100) calculator.update(flight(370.0, -2.0), time)
        assertEquals(-2.0, calculator.update(flight(370.0, -2.0), 2300).specificExcessPowerMps)
        assertNull(calculator.update(flight(500.0).copy(aircraft = "other"), 2400).specificExcessPowerMps)
        assertNull(calculator.update(flight(500.0), 5000).specificExcessPowerMps)
        assertNull(calculator.update(flight(510.0), 5000).specificExcessPowerMps)
        assertNull(calculator.update(flight(520.0), 4900).specificExcessPowerMps)
        assertNull(calculator.update(flight(520.0).copy(tasKmh = null), 5100).specificExcessPowerMps)
        assertNull(calculator.update(flight(530.0), 5200).specificExcessPowerMps)
        calculator.reset()
        assertNull(calculator.update(flight(540.0), 5300).specificExcessPowerMps)
    }
}
