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

    @Test fun delayedSamplesDoNotReplayArrivalButRealTransitionsStillDo() {
        val cue = FlightArrivalCue()
        assertTrue(cue.update(flight, 0, true))
        assertFalse(cue.update(ConnectionState.Delayed, 20000, true))
        assertFalse(cue.update(flight, 20500, true), "Cooldown expiry must not turn recovery into arrival")
        assertFalse(cue.update(ConnectionState.Delayed, 40000, false))
        assertFalse(cue.update(flight, 40500, true), "Enabling sound during a delay must not replay arrival")
        val changed = flight.copy(telemetry = flight.telemetry.copy(aircraft = "second"))
        cue.update(ConnectionState.Delayed, 50000, true)
        assertTrue(cue.update(changed, 50500, true))
        cue.update(ConnectionState.Delayed, 70000, true)
        cue.update(ConnectionState.Disconnected("timeout"), 71500, true)
        assertTrue(cue.update(changed, 72000, true))
        cue.update(ConnectionState.Delayed, 90000, true)
        cue.update(ConnectionState.WaitingForFlight, 90500, true)
        assertTrue(cue.update(changed, 91000, true))
    }

    @Test fun initialDelayedRequestDoesNotConsumeTheFirstArrival() {
        val cue = FlightArrivalCue()
        assertFalse(cue.update(ConnectionState.Delayed, 1000, true))
        assertTrue(cue.update(flight, 1500, true))
    }
}
