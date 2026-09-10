package voidmei.telemetry

import voidmei.fm.*
import kotlin.test.*

class AircraftAlertModelTest {
    private fun model(aircraft: String = "test", flapSpeed: Int = 400) = AircraftAlertModel(aircraft,
        FlightModelExtractor.extract(BlkParser.parse("""
            Vne:r=800
            VneMach:r=2
            NoFlaps { alphaCritLow:r=-10; alphaCritHigh:r=20 }
            FullFlaps { alphaCritLow:r=-10; alphaCritHigh:r=20 }
            GearDestructionIndSpeed:r=450
            FlapsDestructionIndSpeedP:p4=0.5,$flapSpeed,1,300
        """)))
    private fun flight(name: String? = "test", ias: Double = 800.0, flaps: Double = 50.0) = ConnectionState.Flying(
        TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!.copy(aircraft = name,
            iasKmh = ias, mach = 2.0, angleOfAttackDeg = 19.1, flapsPercent = flaps,
            gearPercent = 50.0, fuelKg = 5.0, fuelCapacityKg = 100.0), FlightMetrics())

    @Test fun staleOrUnidentifiedAircraftCannotUseAnyModelLimit() {
        val evaluator = FlightAlerts()
        val bound = model()
        val active = evaluator.updateForAircraft(flight(), bound, 0, false).active
        assertTrue(active.containsAll(listOf(FlightAlert.CRITICAL_AOA, FlightAlert.IAS_LIMIT,
            FlightAlert.MACH_LIMIT, FlightAlert.GEAR_LIMIT, FlightAlert.FLAP_LIMIT)))
        for (name in listOf("other", null)) {
            assertEquals(listOf(FlightAlert.LOW_FUEL), evaluator.updateForAircraft(flight(name), bound, 100, false).active)
        }
        assertTrue(evaluator.updateForAircraft(flight("TEST"), bound, 200, false).active.contains(FlightAlert.FLAP_LIMIT))
    }

    @Test fun withdrawalAndReloadReplaceLimitsWithoutResettingUnrelatedVoiceCooldown() {
        val evaluator = FlightAlerts()
        val state = flight(ias = 400.0).let { it.copy(telemetry = it.telemetry.copy(mach = 0.0, angleOfAttackDeg = 0.0)) }
        assertEquals(listOf(FlightAlert.FLAP_LIMIT, FlightAlert.LOW_FUEL), evaluator.updateForAircraft(state, model(), 0, false).active)
        assertEquals(listOf(FlightAlert.LOW_FUEL), evaluator.updateForAircraft(state, null, 100, true).active)
        val reloaded = evaluator.updateForAircraft(state, model(flapSpeed = 600), 200, true)
        assertEquals(listOf(FlightAlert.LOW_FUEL), reloaded.active)
        assertNull(reloaded.voice) // Reload does not replay a fuel warning emitted at t=100.
        assertEquals(listOf(FlightAlert.FLAP_LIMIT, FlightAlert.LOW_FUEL), evaluator.updateForAircraft(state, model(), 300, false).active)
    }

    @Test fun validMotionHistorySurvivesModelWithdrawalButNotAircraftSwitch() {
        val evaluator = FlightAlerts()
        val bound = model(flapSpeed = 500)
        evaluator.updateForAircraft(flight(ias = 500.0, flaps = 40.0), bound, 0, false)
        evaluator.updateForAircraft(flight(ias = 500.0, flaps = 42.0), null, 100, false)
        assertTrue(evaluator.updateForAircraft(flight(ias = 500.0, flaps = 43.0), bound, 200, false).active.contains(FlightAlert.FLAP_LIMIT))
        val nextAircraft = model("other", 500)
        assertFalse(evaluator.updateForAircraft(flight("other", 500.0, 44.0), nextAircraft, 300, false).active.contains(FlightAlert.FLAP_LIMIT))
    }
}
