package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class FlightPerformanceMonitorTest {
    private val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
    private fun flight(alt: Double? = null, speed: Double? = null, roll: Double? = null,
        alr: Double? = null, g: Double? = null, elev: Double? = null, sep: Double? = null) =
        ConnectionState.Flying(telemetry.copy(altitudeM = alt, iasKmh = speed, rollRateDegPerSecond = roll,
            aileronPercent = alr, loadG = g, elevatorPercent = elev), FlightMetrics(specificExcessPowerMps = sep))

    @Test fun climbUsesActualStartingAltitudeAndElapsedTimeAndRecoversAfterSkippedBands() {
        val monitor = FlightPerformanceMonitor()
        assertTrue(monitor.update(flight(150.0), 500).isEmpty())
        assertEquals(listOf(PerformanceObservation.Climb(200, 5.0, 10.0)), monitor.update(flight(200.0), 5500))
        assertEquals(listOf(PerformanceObservation.Climb(400, 10.0, 25.0)), monitor.update(flight(400.0), 10500))
        assertTrue(monitor.update(flight(100.0), 11500).isEmpty())
        assertTrue(monitor.update(flight(400.0), 12500).isEmpty())
        assertTrue(monitor.update(flight(null), 13500).isEmpty())
        assertTrue(monitor.update(flight(550.0), 14500).isEmpty())
        assertEquals(listOf(PerformanceObservation.Climb(600, 5.0, 10.0)), monitor.update(flight(600.0), 19500))
        assertTrue(monitor.update(flight(1000.0), 19500).isEmpty())
    }

    @Test fun rollRequiresValidControlsAndKeepsPerSpeedBinRecords() {
        val monitor = FlightPerformanceMonitor()
        assertTrue(monitor.update(flight(speed = 200.0, roll = 100.0), 0).isEmpty())
        assertEquals(listOf(PerformanceObservation.Roll(200, 100.0)), monitor.update(flight(speed = 204.0, roll = -100.0, alr = -50.0), 100))
        assertTrue(monitor.update(flight(speed = 200.0, roll = 200.0, alr = 40.0), 200).isEmpty())
        assertTrue(monitor.update(flight(speed = 200.0, roll = 130.0, alr = 50.0), 300).isEmpty())
        assertEquals(listOf(PerformanceObservation.Roll(210, 100.0)), monitor.update(flight(speed = 205.0, roll = 100.0, alr = 50.0), 400))
        assertTrue(monitor.update(flight(speed = Double.NaN, roll = 200.0, alr = 50.0), 500).isEmpty())
        assertTrue(monitor.update(flight(speed = 2560.0, roll = 200.0, alr = 50.0), 600).isEmpty())
    }

    @Test fun turnRetainsLegacySmoothingButRequiresFiniteSepAndControls() {
        val monitor = FlightPerformanceMonitor()
        assertTrue(monitor.update(flight(speed = 300.0, g = 8.0, elev = 80.0), 0).isEmpty())
        assertTrue(monitor.update(flight(speed = 300.0, g = 8.0, elev = 80.0, sep = 5.0), 100).isEmpty())
        assertEquals(listOf(PerformanceObservation.Turn(300, 4.0, -1.0)), monitor.update(flight(speed = 300.0, g = 8.0, elev = 80.0, sep = -2.0), 200))
        assertTrue(monitor.update(flight(speed = 300.0, g = 12.0, elev = 70.0, sep = -2.0), 300).isEmpty())
    }
}
