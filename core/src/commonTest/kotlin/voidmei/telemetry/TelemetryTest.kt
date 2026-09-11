package voidmei.telemetry

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {
    private val indicators = """{"valid":true,"army":"air","type":"p-51d"}"""
    private fun parse(fields: String = "") = TelemetryParser.parse("""{"valid":true$fields}""", indicators)!!

    @Test fun exactKeysAndMissingValues() {
        val t = parse(""", "Mfuel0, kg":734,"Mfuel, kg":197,"Mfuel 1, kg":42,"IAS, km/h":474,"M":0.39""")
        assertEquals(197.0, t.fuelKg)
        assertEquals(734.0, t.fuelCapacityKg)
        assertEquals(474.0, t.iasKmh)
        assertNull(t.tasKmh)
        assertEquals(0.39, t.mach)
    }

    @Test fun invalidAndNonAircraftClearFlight() {
        assertNull(TelemetryParser.parse("""{"valid":false}""", indicators))
        assertNull(TelemetryParser.parse("""{"valid":"true"}""", indicators))
        assertNull(TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"army":"tank"}"""))
        assertFails { TelemetryParser.parse("{", indicators) }
    }

    @Test fun arbitraryEngineCountAndSparseEngines() {
        val t = parse(""", "throttle 1, %":110,"thrust 10, kgs":1000,"RPM 14":1200""")
        assertEquals(listOf(1, 10, 14), t.engines.map { it.index })
        assertEquals(110.0, t.engines[0].throttlePercent)
        assertNull(t.engines[0].thrustKgf)
        assertEquals(1000.0, t.engines[1].thrustKgf)
    }

    @Test fun unusableNumbersAreAbsent() {
        val t = parse(""", "IAS, km/h":-65535,"H, m":1e999,"M":"fast","Ny":null""")
        assertNull(t.iasKmh)
        assertNull(t.altitudeM)
        assertNull(t.mach)
        assertNull(t.loadG)
    }

    @Test fun engineDiscoveryRequiresCanonicalIndicesAndExactSupportedUnits() {
        val t = parse(""", "RPM 01":1000,"RPM 0":1000,"RPM -1":1000,"RPM +2":1000,
            "RPM 2147483648":1000,"RPM 3, rad/s":1000,"power 4, kW":100,
            "throttle 5":100,"magneto 6, %":100,"oil temp 7, F":100,
            "RPM 8 ":1000,"future engine 9":100,"RPM 10":1234""")
        assertEquals(listOf(10), t.engines.map { it.index })
        assertEquals(1234.0, t.engines.single().rpm)
    }

    @Test fun supportedSparseEngineFieldsStillEstablishAnEngineWhenItsReadingIsMissing() {
        val t = parse(""", "throttle 1, %":null,"RPM 2":null,"power 3, hp":null,
            "thrust 4, kgs":null,"water temp 5, C":null,"oil temp 6, C":null,
            "RPM throttle 7, %":null,"mixture 8, %":null,"radiator 9, %":null,
            "oil radiator 10, %":null,"compressor stage 11":null,"magneto 12":null,
            "manifold pressure 13, atm":null,"pitch 14, deg":null,"efficiency 15, %":null""")
        assertEquals((1..15).toList(), t.engines.map { it.index })
        assertTrue(t.engines.all { it.rpm == null && it.throttlePercent == null })
    }

    @Test fun energyUsesMetresPerSecondAndResetsOnSwitchAndGaps() {
        val calculator = FlightCalculator()
        val first = parse(""", "TAS, km/h":360,"H, m":1000,"Vy, m/s":5""")
        val start = calculator.update(first, 0)
        assertEquals(1000 + 10000 / (2 * FlightCalculator.G), start.energyHeightM!!, 1e-8)
        assertNull(start.accelerationMps2)
        val second = first.copy(tasKmh = 396.0)
        val result = calculator.update(second, 1000)
        assertEquals(10.0, result.accelerationMps2!!, 1e-8)
        assertEquals(5 + 1050 / FlightCalculator.G, result.specificExcessPowerMps!!, 1e-8)
        assertNull(calculator.update(second.copy(aircraft = "yak"), 1100).accelerationMps2)
        assertNull(calculator.update(second, 5000).accelerationMps2)
        calculator.reset()
        assertNull(calculator.update(second, 5100).accelerationMps2)
    }

    @Test fun pollerRecoversAndDoesNotKeepStaleTelemetry() = runTest {
        var round = 0
        val transport = TelemetryTransport { path ->
            if (path == "/indicators") indicators else when (round++) {
                0 -> """{"valid":true,"TAS, km/h":360}"""
                1 -> error("offline")
                2 -> """{"valid":false}"""
                else -> """{"valid":true,"TAS, km/h":720}"""
            }
        }
        val states = TelemetryPoller(transport).states().take(5).toList()
        assertIs<ConnectionState.Connecting>(states[0])
        assertIs<ConnectionState.Flying>(states[1])
        assertIs<ConnectionState.Disconnected>(states[2])
        assertIs<ConnectionState.WaitingForFlight>(states[3])
        assertNull(assertIs<ConnectionState.Flying>(states[4]).metrics.accelerationMps2)
    }

    @Test fun timeoutCancelsBothRequestsAndCollectorCancellationStopsPolling() = runTest {
        var cancelled = 0
        val transport = TelemetryTransport {
            try { awaitCancellation() } finally { cancelled++ }
        }
        val states = TelemetryPoller(transport).states().take(3).toList()
        assertEquals(ConnectionState.Delayed, states[1])
        assertIs<ConnectionState.Disconnected>(states[2])
        assertEquals(2, cancelled)
        val job = launch { TelemetryPoller(transport).states().collect() }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(4, cancelled)
    }
}
