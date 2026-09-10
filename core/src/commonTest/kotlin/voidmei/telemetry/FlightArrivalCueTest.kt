package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class FlightArrivalCueTest {
    private val flight = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""",
        """{"valid":true,"type":"first"}""")!!, FlightMetrics())

    @Test fun playsOncePerArrivalAndSuppressesShortReconnects() {
        val cue = FlightArrivalCue()
        assertFalse(cue.update(ConnectionState.Connecting, 0, true))
        assertTrue(cue.update(flight, 100, true))
        assertFalse(cue.update(flight, 20000, true))
        assertFalse(cue.update(ConnectionState.Disconnected("lost"), 20100, true))
        assertTrue(cue.update(flight, 20200, true))
        cue.update(ConnectionState.WaitingForFlight, 20300, true)
        assertFalse(cue.update(flight, 20400, true))
        assertFalse(cue.update(flight, 40000, true), "Do not queue suppressed greetings")
        assertTrue(cue.update(flight.copy(telemetry = flight.telemetry.copy(aircraft = "second")), 40100, true))
    }

    @Test fun mutingConsumesArrivalAndOldSettingImportsTheVoiceChoice() {
        val cue = FlightArrivalCue()
        assertFalse(cue.update(flight, 0, false))
        assertFalse(cue.update(flight, 100, true))
        for (enabled in listOf(true, false)) {
            val imported = LegacySettingsReader.read("""(panel p (item sound :type voice :target "voice_start1" :value "custom|$enabled"))""")
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(VoiceChoice(enabled, "custom"), imported.applyTo(AppSettings()).alertVoices["start1"])
        }
        assertNull(AppSettings().alertVoices["start1"])
    }
}
