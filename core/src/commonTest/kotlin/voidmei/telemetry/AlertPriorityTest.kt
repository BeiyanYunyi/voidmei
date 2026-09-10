package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class AlertPriorityTest {
    private fun flight() = ConnectionState.Flying(TelemetryParser.parse(
        """{"valid":true,"IAS, km/h":780,"AoA, deg":16,"Ny":11}""",
        """{"valid":true,"type":"test"}""")!!, FlightMetrics())
    private val limits = WingLimits(800.0, null, null, 20.0)

    @Test fun structuralAndSpeedWarningsPrecedeHighAngleAdvisory() {
        val result = FlightAlerts().update(flight(), limits, 0, true, loadLimits = LoadLimits(-4.0, 10.0))
        assertEquals(listOf(FlightAlert.LOAD_LIMIT, FlightAlert.IAS_LIMIT, FlightAlert.HIGH_AOA), result.active)
        assertEquals(FlightAlert.LOAD_LIMIT, result.voice)
    }

    @Test fun unavailableVoiceKeepsVisualAndDoesNotSpendCooldown() {
        val evaluator = FlightAlerts()
        val waiting = evaluator.update(flight(), limits, 0, true, voiceAvailable = { false })
        assertEquals(listOf(FlightAlert.IAS_LIMIT, FlightAlert.HIGH_AOA), waiting.active)
        assertNull(waiting.voice)
        assertEquals(FlightAlert.IAS_LIMIT, evaluator.update(flight(), limits, 100, true).voice)
        // After the warning, an available lower-level voice may run during its cooldown.
        assertEquals(FlightAlert.HIGH_AOA, evaluator.update(flight(), limits, 2100, true).voice)
    }

    @Test fun disabledWarningsDoNotBlockAdvisoriesAndModelEntryPassesAvailability() {
        assertEquals(FlightAlert.HIGH_AOA, FlightAlerts().update(flight(), limits, 0, true,
            disabledVoices = setOf("warn_ias")).voice)
        val model = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("Vne:r=800")))
        val evaluator = FlightAlerts()
        assertNull(evaluator.updateForAircraft(flight(), model, 0, true, voiceAvailable = { false }).voice)
        assertEquals(FlightAlert.IAS_LIMIT, evaluator.updateForAircraft(flight(), model, 100, true).voice)
    }
}
