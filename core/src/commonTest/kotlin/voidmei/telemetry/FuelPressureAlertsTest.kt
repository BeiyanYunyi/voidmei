package voidmei.telemetry

import kotlin.test.*

class FuelPressureAlertsTest {
    private fun parse(value: String = "9.7") = TelemetryParser.parse(
        """{"valid":true,"throttle 1, %":100}""",
        """{"valid":true,"type":"test","fuel_pressure":$value,"fuel_pressure1":123}""")!!
    private fun flight(pressure: Double? = 9.7) = ConnectionState.Flying(parse().copy(fuelPressureRaw = pressure), FlightMetrics())

    @Test fun gaugeParsingRequiresExactFiniteNonnegativeNumber() {
        assertEquals(9.7, parse().fuelPressureRaw)
        assertEquals(0.0, parse("0").fuelPressureRaw)
        for (raw in listOf("null", "true", "\"9.7\"", "-65535", "-1", "1e999"))
            assertNull(parse(raw).fuelPressureRaw)
    }

    @Test fun unsupportedEngineKeysDoNotDisableSingleEnginePressureMonitoring() {
        val telemetry = TelemetryParser.parse(
            """{"valid":true,"throttle 1, %":100,"RPM 02":1000,"power 3, kW":100}""",
            """{"valid":true,"fuel_pressure":9.7}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics())
        val alerts = FlightAlerts()
        assertTrue(alerts.updateForAircraft(flight, null, 0, false).active.isEmpty())
        assertEquals(listOf(FlightAlert.LOW_FUEL_PRESSURE),
            alerts.updateForAircraft(flight, null, 2000, false).active)
    }

    @Test fun strictThresholdRequiresTwoSecondsAndUsesThirtySecondVoiceCooldown() {
        val alerts = FlightAlerts()
        fun tick(time: Long, pressure: Double? = 9.7) = alerts.updateForAircraft(flight(pressure), null, time, true)
        assertTrue(tick(0, 9.8).active.isEmpty()) // Difference exactly two.
        assertTrue(tick(2000, 9.8).active.isEmpty())
        assertTrue(tick(2100).active.isEmpty())
        assertTrue(tick(4099).active.isEmpty())
        assertEquals(FlightAlert.LOW_FUEL_PRESSURE, tick(4100).voice)
        for (time in 6100L..32100L step 2000) {
            val update = tick(time)
            assertEquals(listOf(FlightAlert.LOW_FUEL_PRESSURE), update.active)
            assertNull(update.voice)
        }
        assertEquals(FlightAlert.LOW_FUEL_PRESSURE, tick(34100).voice)
        assertTrue(tick(34200, 10.0).active.isEmpty())
    }

    @Test fun missingDataAndLongGapsCannotAccumulateTime() {
        val alerts = FlightAlerts()
        fun tick(time: Long, pressure: Double? = 9.7) = alerts.updateForAircraft(flight(pressure), null, time, false)
        tick(0)
        assertTrue(tick(3000).active.isEmpty())
        tick(4000, null)
        assertTrue(tick(5000).active.isEmpty())
        assertTrue(tick(6999).active.isEmpty())
        assertEquals(listOf(FlightAlert.LOW_FUEL_PRESSURE), tick(7000).active)
        assertTrue(tick(100).active.isEmpty()) // Backward clock.
        alerts.updateForAircraft(ConnectionState.Disconnected("test"), null, 200, false)
        assertTrue(tick(300).active.isEmpty())
        for (pressure in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0))
            assertTrue(tick(1000, pressure).active.isEmpty())
    }

    @Test fun unknownThrottleAndAmbiguousEngineAssociationSuppressDetection() {
        for (engines in listOf(emptyList(), parse().engines + parse().engines,
            parse().engines + parse().engines.map { it.copy(index = 2) },
            parse().engines.map { it.copy(index = 0) },
            parse().engines.map { it.copy(throttlePercent = null) },
            parse().engines.map { it.copy(throttlePercent = Double.NaN) })) {
            val alerts = FlightAlerts()
            val state = flight(0.0).copy(telemetry = parse("0").copy(engines = engines))
            alerts.updateForAircraft(state, null, 0, true)
            assertTrue(alerts.updateForAircraft(state, null, 2000, true).active.isEmpty())
        }
    }

    @Test fun mutedWarningsStayVisibleWithoutInferringEngineDamage() {
        val alerts = FlightAlerts()
        alerts.updateForAircraft(flight(0.0), null, 0, true)
        val muted = alerts.updateForAircraft(flight(0.0), null, 2000, true, setOf("warn_lowpressure"))
        assertEquals(listOf(FlightAlert.LOW_FUEL_PRESSURE), muted.active)
        assertNull(muted.voice)
        assertEquals(FlightAlert.LOW_FUEL_PRESSURE, alerts.updateForAircraft(flight(0.0), null, 2100, true).voice)
    }
}
