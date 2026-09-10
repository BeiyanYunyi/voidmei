package voidmei.telemetry

import kotlin.math.exp
import kotlin.test.*
import voidmei.config.*
import voidmei.fm.*

class EngineResponseTest {
    private val t = TelemetryParser.parse("""{"valid":true,"power 1, hp":0}""",
        """{"valid":true,"type":"test"}""")!!
    private fun frame(percent: Double, reference: Double = 1000.0) = ConnectionState.Flying(t,
        FlightMetrics(observedEnginePeak = ObservedEnginePeak(EnginePeakKind.SHAFT_POWER_HP, percent, reference)))
    private fun EngineResponseMonitor.rate(percent: Double, time: Long, reference: Double = 1000.0) =
        (update(frame(percent, reference), null, time) as ConnectionState.Flying).metrics.engineResponsePercentPerSecond

    @Test fun smoothingUsesElapsedTimeAndPreservesDirection() {
        for (step in listOf(100L, 200L, 500L, 1000L)) {
            val monitor = EngineResponseMonitor()
            assertNull(monitor.rate(0.0, 0))
            var result: Double? = null
            for (time in step..1000L step step) result = monitor.rate(time / 100.0, time)
            assertEquals(10 * (1 - exp(-1.0)), result!!, 1e-9)
        }
        val monitor = EngineResponseMonitor()
        monitor.rate(100.0, 0)
        assertTrue(monitor.rate(0.0, 1000)!! < 0)
    }

    @Test fun referenceChangesAndFlightDiscontinuitiesNeverCreateSpikes() {
        val monitor = EngineResponseMonitor()
        monitor.rate(50.0, 0)
        assertNull(monitor.rate(25.0, 1000, 2000.0))
        assertEquals(0.0, monitor.rate(25.0, 2000, 2000.0))
        assertNull(monitor.rate(100.0, 5000, 2000.0))
        assertNull(monitor.rate(100.0, 5000, 2000.0))
        monitor.update(ConnectionState.Disconnected("test"), null, 6000)
        assertNull(monitor.rate(100.0, 7000, 2000.0))
        val other = frame(30.0).copy(telemetry = t.copy(aircraft = "other"))
        assertNull((monitor.update(other, null, 8000) as ConnectionState.Flying).metrics.engineResponsePercentPerSecond)
        assertNull((monitor.update(frame(30.0).copy(metrics = FlightMetrics()), null, 9000) as ConnectionState.Flying)
            .metrics.engineResponsePercentPerSecond)
    }

    @Test fun oldResponseSwitchImportsWithoutReplacingOtherFields() {
        val imported = LegacySettingsReader.read("""(panel p (item response :type data :target getEngineResponse :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("power_percent")))
        assertEquals(listOf("power_percent", "engine_response"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
