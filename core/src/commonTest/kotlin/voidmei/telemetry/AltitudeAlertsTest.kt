package voidmei.telemetry

import kotlin.test.*

class AltitudeAlertsTest {
    private fun flight(radio: Double?, height: Double? = 1000.0, descent: Double? = 0.0, gear: Double? = 0.0) =
        ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!.copy(
            radioAltitudeRaw = radio, altitudeM = height, verticalSpeedMps = descent, gearPercent = gear), FlightMetrics())

    @Test fun rawRadarHeightNeverFillsFromBarometricAltitude() {
        fun parse(value: String) = TelemetryParser.parse("""{"valid":true,"H, m":1000}""", """{"valid":true,"radio_altitude":$value}""")!!
        assertEquals(300.0, parse("300").radioAltitudeRaw)
        assertEquals(0.0, parse("0").radioAltitudeRaw)
        for (value in listOf("-65535", "-1", "null", "\"300\"")) assertNull(parse(value).radioAltitudeRaw)
    }

    @Test fun terrainWarningIsInvariantToFeetAndMetresAndNeedsConsecutiveSamples() {
        fun alerts(scale: Double): List<FlightAlert> {
            val evaluator = FlightAlerts()
            assertTrue(evaluator.update(flight(100 * scale), null, 0, true).active.isEmpty())
            return evaluator.update(flight(50 * scale), null, 1000, true).active
        }
        assertEquals(listOf(FlightAlert.TERRAIN_CLOSURE), alerts(1.0))
        assertEquals(alerts(1.0), alerts(1 / .3048))
    }

    @Test fun constantClosureProducesSameDecisionAtDifferentPollingIntervals() {
        for (step in listOf(50L, 100L, 500L, 1000L)) {
            val evaluator = FlightAlerts()
            var last: AlertUpdate? = null
            for (time in 0L..5000L step step) {
                last = evaluator.update(flight(1000.0 - time / 10.0), null, time, true)
            }
            assertEquals(listOf(FlightAlert.TERRAIN_CLOSURE), last!!.active, "interval=$step")
        }
    }

    @Test fun missingDataGapsAndAircraftChangesResetTerrainDerivative() {
        val evaluator = FlightAlerts()
        evaluator.update(flight(100.0), null, 0, true)
        evaluator.update(flight(null), null, 1000, true)
        assertTrue(evaluator.update(flight(20.0), null, 2000, true).active.isEmpty())
        assertTrue(evaluator.update(flight(10.0), null, 5000, true).active.isEmpty())
        val next = flight(1.0).let { it.copy(telemetry = it.telemetry.copy(aircraft = "other")) }
        assertTrue(evaluator.update(next, null, 6000, true).active.isEmpty())
        evaluator.update(ConnectionState.WaitingForFlight, null, 6100, true)
        assertTrue(evaluator.update(flight(1.0), null, 6200, true).active.isEmpty())
    }

    @Test fun altitudeHasPriorityOverTerrainAndRequiresRetractedKnownGear() {
        val evaluator = FlightAlerts()
        evaluator.update(flight(100.0), null, 0, true)
        assertEquals(listOf(FlightAlert.ALTITUDE_DESCENT), evaluator.update(flight(50.0, 100.0, -10.01), null, 1000, true).active)
        assertTrue(FlightAlerts().update(flight(null, 100.0, -10.0), null, 0, true).active.isEmpty())
        for (gear in listOf(null, 1.0, Double.NaN)) {
            assertTrue(FlightAlerts().update(flight(null, 100.0, -11.0, gear), null, 0, true).active.isEmpty())
        }
    }
}
