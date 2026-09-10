package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class EngineRpmAlertsTest {
    private val source = """
        EngineType0 { Main { Type:t=Inline; RPMMaxAllowed:r=3200 } }
        Engine0 { Type:i=0; Main { RPMMaxAllowed:r=3400 } }
        Engine1 { Type:i=0 }
    """
    private val parameters = FlightModelExtractor.extract(BlkParser.parse(source))
    private fun telemetry(first: Double = 3400.0, second: Double = 3199.0) = TelemetryParser.parse(
        """{"valid":true,"RPM 1":$first,"RPM 2":$second}""", """{"valid":true,"type":"test"}""")!!

    @Test fun overridesAndTypeDefaultsYieldSeparateEngineLimits() {
        assertEquals(listOf(EngineRpmLimit(1, 3400.0), EngineRpmLimit(2, 3200.0)), parameters.engineRpmLimits)
        assertEquals(setOf(1), EngineWarnings.highRpm(telemetry(), parameters.engineRpmLimits))
        assertEquals(setOf(2), EngineWarnings.highRpm(telemetry(3399.0, 3200.0), parameters.engineRpmLimits))
        assertEquals(setOf(1, 2), EngineWarnings.highRpm(telemetry(3400.0, 3200.0), parameters.engineRpmLimits))
    }

    @Test fun absentAmbiguousOrInvalidLimitsCannotRaiseWarnings() {
        assertTrue(EngineWarnings.highRpm(telemetry(), emptyList()).isEmpty())
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertTrue(EngineWarnings.highRpm(telemetry(), listOf(EngineRpmLimit(1, bad))).isEmpty())
        assertTrue(EngineWarnings.highRpm(telemetry(), listOf(EngineRpmLimit(1, 3200.0), EngineRpmLimit(1, 3400.0))).isEmpty())
        val duplicate = telemetry().let { it.copy(engines = listOf(it.engines.first(), it.engines.first())) }
        assertTrue(EngineWarnings.highRpm(duplicate, parameters.engineRpmLimits).isEmpty())
        val unknown = telemetry().let { it.copy(engines = it.engines.map { engine -> engine.copy(rpm = null) }) }
        assertTrue(EngineWarnings.highRpm(unknown, parameters.engineRpmLimits).isEmpty())
        val invalid = FlightModelExtractor.extract(BlkParser.parse(source.replace("RPMMaxAllowed:r=3400", "RPMMaxAllowed:r=-1")))
        assertEquals(listOf(EngineRpmLimit(2, 3200.0)), invalid.engineRpmLimits)
        assertTrue(invalid.issues.any { it.contains("Engine0.Main.RPMMaxAllowed") })
    }

    @Test fun alertBindingRequiresCurrentAircraftAndRetainsVoiceCooldown() {
        val bound = AircraftAlertModel("test", parameters)
        val evaluator = FlightAlerts()
        val flight = ConnectionState.Flying(telemetry(), FlightMetrics())
        assertEquals(FlightAlert.HIGH_RPM, evaluator.updateForAircraft(flight, bound, 0, true).voice)
        for (time in 2000L..8000L step 2000) {
            val update = evaluator.updateForAircraft(flight, bound, time, true)
            assertEquals(listOf(FlightAlert.HIGH_RPM), update.active)
            assertNull(update.voice)
        }
        assertEquals(FlightAlert.HIGH_RPM, evaluator.updateForAircraft(flight, bound, 10000, true).voice)
        assertTrue(evaluator.updateForAircraft(flight, null, 11000, true).active.isEmpty())
        val other = flight.copy(telemetry = flight.telemetry.copy(aircraft = "other"))
        assertTrue(evaluator.updateForAircraft(other, bound, 12000, true).active.isEmpty())
    }
}
