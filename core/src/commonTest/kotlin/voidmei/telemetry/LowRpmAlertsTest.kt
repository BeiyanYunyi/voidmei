package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class LowRpmAlertsTest {
    private val parameters = FlightModelExtractor.extract(BlkParser.parse("""
        EngineType0 { Main { Type:t=Inline; RPMMax:r=3000 } }
        EngineType1 { Main { Type:t=Jet; RPMMax:r=10000 } }
        Engine0 { Type:i=0 }
        Engine1 { Type:i=1 }
    """))
    private fun telemetry() = TelemetryParser.parse("""{"valid":true,
        "RPM 1":2100,"throttle 1, %":100,"RPM throttle 1, %":100,"thrust 1, kgs":100,
        "RPM 2":7000,"throttle 2, %":100,"thrust 2, kgs":100}""", """{"valid":true,"type":"test"}""")!!

    @Test fun usesIndependentNominalReferencesAndStrictThrottleBoundary() {
        assertTrue(parameters.engineRpmLimits.isEmpty()) // A destruction limit is not the normal-speed reference.
        assertEquals(listOf(EngineRpmReference(1, 3000.0, "Inline"), EngineRpmReference(2, 10000.0, "Jet")), parameters.engineRpmReferences)
        val base = telemetry()
        assertTrue(EngineWarnings.lowRpm(base, parameters.engineRpmReferences).isEmpty())
        val low = base.copy(engines = base.engines.map { it.copy(rpm = it.rpm!! - 1) })
        assertEquals(setOf(1, 2), EngineWarnings.lowRpm(low, parameters.engineRpmReferences))
        val differentThrottle = low.copy(engines = low.engines.map { if (it.index == 1) it.copy(throttlePercent = 30.0) else it })
        assertEquals(setOf(2), EngineWarnings.lowRpm(differentThrottle, parameters.engineRpmReferences))
    }

    @Test fun pistonNeedsControlDataAndInvertedLowThrustExclusionIsPerEngine() {
        val base = telemetry().let { it.copy(engines = it.engines.map { engine -> engine.copy(rpm = 100.0) }) }
        for (control in listOf(null, -1.0, Double.NaN)) {
            val unknown = base.copy(engines = base.engines.map { it.copy(rpmControlPercent = control) })
            assertEquals(setOf(2), EngineWarnings.lowRpm(unknown, parameters.engineRpmReferences))
        }
        val inverted = base.copy(loadG = -1.0, engines = base.engines.map { if (it.index == 1) it.copy(thrustKgf = 10.0) else it })
        assertEquals(setOf(2), EngineWarnings.lowRpm(inverted, parameters.engineRpmReferences))
    }

    @Test fun unknownValuesAndAmbiguousIndicesDoNotProduceLowRpmWarnings() {
        val base = telemetry()
        for (rpm in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val unknown = base.copy(engines = base.engines.map { it.copy(rpm = rpm) })
            assertTrue(EngineWarnings.lowRpm(unknown, parameters.engineRpmReferences).isEmpty())
        }
        val low = base.engines.first().copy(rpm = 100.0)
        assertTrue(EngineWarnings.lowRpm(base.copy(engines = listOf(low, low)), parameters.engineRpmReferences).isEmpty())
        assertTrue(EngineWarnings.lowRpm(base.copy(engines = listOf(low)), parameters.engineRpmReferences + parameters.engineRpmReferences).isEmpty())
        assertTrue(EngineWarnings.lowRpm(base.copy(engines = listOf(low)), listOf(EngineRpmReference(1, 3000.0, "Unknown"))).isEmpty())
    }

    @Test fun alertUsesCurrentAircraftAndTenSecondCooldown() {
        val bound = AircraftAlertModel("test", parameters)
        val base = telemetry().let { it.copy(engines = it.engines.map { engine -> engine.copy(rpm = 100.0) }) }
        val flight = ConnectionState.Flying(base, FlightMetrics())
        val evaluator = FlightAlerts()
        assertEquals(FlightAlert.LOW_RPM, evaluator.updateForAircraft(flight, bound, 0, true).voice)
        for (time in 2000L..8000L step 2000) assertNull(evaluator.updateForAircraft(flight, bound, time, true).voice)
        assertEquals(FlightAlert.LOW_RPM, evaluator.updateForAircraft(flight, bound, 10000, true).voice)
        assertTrue(evaluator.updateForAircraft(flight, null, 11000, true).active.isEmpty())
        assertTrue(evaluator.updateForAircraft(flight.copy(telemetry = base.copy(aircraft = "other")), bound, 12000, true).active.isEmpty())
    }
}
