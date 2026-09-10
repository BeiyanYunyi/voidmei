package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class SpeedLimitScaleTest {
    @Test fun stallMarkerTracksFuelFlapsSweepAndTheActiveSpeedLimit() {
        val t = TelemetryParser.parse("""{"valid":true,"IAS, km/h":400,"M":0.5,"Mfuel, kg":0,"flaps, %":0}""",
            """{"valid":true,"type":"test","wing_sweep_indicator":0}""")!!
        val forward = WingConfiguration(0.0, 500.0, 1.0, null, null, null, null)
        val swept = forward.copy(sweep = 1.0, vneKmh = 600.0)
        val stall = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0), StallLiftProfile(1.0, 5.0, 10.0)))
        val model = AircraftAlertModel("test", FlightModelParameters(null, null, listOf(forward, swept), true, emptyList(), stallSpeed = stall))
        fun read(flight: Telemetry) = SpeedLimitScale.fromFlight(ConnectionState.Flying(flight, FlightMetrics()), model)
        assertEquals(144.0 / 500, read(t)!!.stallRatio!!, 1e-12)
        assertEquals(144 * kotlin.math.sqrt(2.0) / 500, read(t.copy(fuelKg = 1000.0))!!.stallRatio!!, 1e-12)
        assertEquals(144 / kotlin.math.sqrt(2.0) / 500, read(t.copy(flapsPercent = 100.0))!!.stallRatio!!, 1e-12)
        assertEquals(144 * kotlin.math.sqrt(2.0) / 600, read(t.copy(wingSweepRatio = 1.0))!!.stallRatio!!, 1e-12)
        assertEquals(144.0 / 320, read(t.copy(mach = 1.25))!!.stallRatio!!, 1e-12)
        assertNull(read(t.copy(fuelKg = null))!!.stallRatio)
        assertNull(read(t.copy(flapsPercent = null))!!.stallRatio)
        assertNull(read(t.copy(wingSweepRatio = null)))
    }

    @Test fun scaleChoosesStricterLimitAndDoesNotInventMissingMarkers() {
        val base = TelemetryParser.parse("""{"valid":true,"IAS, km/h":400,"M":0.5,"flaps, %":0}""",
            """{"valid":true,"type":"test"}""")!!
        val wing = WingConfiguration(0.0, 500.0, 1.0, null, null, null, null)
        val parameters = FlightModelParameters(null, null, listOf(wing), false, emptyList(),
            controlSpeeds = ControlEffectiveSpeeds(250.0, null, 400.0))
        val model = AircraftAlertModel("test", parameters)
        fun read(t: Telemetry = base, m: AircraftAlertModel? = model) =
            SpeedLimitScale.fromFlight(ConnectionState.Flying(t, FlightMetrics()), m)
        val ias = read()!!
        assertEquals(0.8, ias.ratio)
        assertFalse(ias.limitingMach)
        assertEquals(0.5, ias.aileronRatio)
        assertEquals(0.8, ias.rudderRatio)
        assertEquals(1.6, ias.machOneRatio)
        assertNull(ias.stallRatio)
        val mach = read(base.copy(mach = 1.25))!!
        assertEquals(1.25, mach.ratio)
        assertTrue(mach.limitingMach)
        assertEquals(250.0 / 320, mach.aileronRatio)
        assertEquals(1.0, mach.machOneRatio)
        assertEquals(0.0, read(base.copy(iasKmh = 0.0, mach = 0.0))!!.ratio)
        assertNull(read(base.copy(iasKmh = 0.0, mach = 0.0))!!.machOneRatio)
        assertNull(read(base.copy(iasKmh = 0.0, mach = 0.5))!!.aileronRatio)
        assertNull(read(base.copy(aircraft = "other")))
        assertNull(read(m = null))
        for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(read(base.copy(iasKmh = invalid)))
            assertNull(read(base.copy(mach = invalid)))
        }
        assertNull(read(m = AircraftAlertModel("test", parameters.copy(wings = listOf(wing.copy(maxMach = null))))))
    }
}
