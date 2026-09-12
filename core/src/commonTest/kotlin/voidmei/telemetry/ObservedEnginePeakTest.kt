package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class ObservedEnginePeakTest {
    @Test fun negativeMagnetoAndMissingPitchPermitJetHistoryWithoutMisclassifyingTurboprops() {
        fun parsed(magneto: String, pitch: String = "-65535") = TelemetryParser.parse(
            """{"valid":true,"power 1, hp":0,"thrust 1, kgs":1000,"throttle 1, %":100,
                "magneto 1":$magneto,"pitch 1, deg":$pitch}""", """{"valid":true,"type":"jet"}""")!!
        for (magneto in listOf("-1", "-65535", "null")) {
            val jet = parsed(magneto)
            val calculator = FlightCalculator()
            for (i in 0..4) assertNull(calculator.update(jet, i * 1000L).observedEnginePeak)
            assertEquals(EnginePeakKind.THRUST_KGF, calculator.update(jet, 5000).observedEnginePeak?.kind, magneto)
            val low = jet.copy(engines = jet.engines.map { it.copy(thrustKgf = 500.0, throttlePercent = 50.0) })
            assertEquals(50.0, calculator.update(low, 6000).observedEnginePeak?.percent, magneto)
            val prop = parsed(magneto, "20")
            assertNull(calculator.update(prop, 7000).observedEnginePeak)
            for (i in 8..14) assertNull(calculator.update(prop, i * 1000L).observedEnginePeak)
        }
    }

    private val base = TelemetryParser.parse("""{"valid":true,"power 1, hp":1000,"throttle 1, %":100,"magneto 1":3}""",
        """{"valid":true,"type":"test"}""")!!
    private fun changed(power: Double = 500.0, throttle: Double = 50.0) = base.copy(
        engines = base.engines.map { it.copy(powerHp = power, throttlePercent = throttle) })

    @Test fun delayedSamplingKeepsConfirmedReferenceButDoesNotBridgeFullThrottleTrials() {
        val c = FlightCalculator()
        for (i in 0..5) c.update(base, i * 1000L)
        c.pause()
        c.pause()
        assertEquals(50.0, c.update(changed(), 8000).observedEnginePeak?.percent)
        assertEquals(1000.0, c.update(changed(), 9000).observedEnginePeak?.reference)
        // A larger candidate observed for less than five continuous seconds must be discarded.
        c.update(changed(2000.0, 100.0), 10000)
        c.update(changed(2000.0, 100.0), 11000)
        c.pause()
        for (i in 14..18) assertEquals(1000.0, c.update(base, i * 1000L).observedEnginePeak?.reference)
        assertEquals(1000.0, c.update(base, 19000).observedEnginePeak?.reference)
        val warming = FlightCalculator()
        for (i in 0..4) warming.update(base, i * 1000L)
        warming.pause()
        for (i in 7..11) assertNull(warming.update(base, i * 1000L).observedEnginePeak)
        assertEquals(1000.0, warming.update(base, 12000).observedEnginePeak?.reference)
    }

    @Test fun pausedReferenceStillRejectsChangedIdentityMissingInputsAndClockRollback() {
        for (next in listOf(changed().copy(aircraft = "other"), changed().copy(aircraft = null),
            changed().copy(engines = base.engines.map { it.copy(index = 2) }),
            changed().copy(engines = base.engines.map { it.copy(powerHp = null) }),
            changed().copy(engines = base.engines.map { it.copy(throttlePercent = null) }),
            changed().copy(engines = base.engines.map { it.copy(powerHp = 0.0, magneto = null, thrustKgf = 1000.0) }))) {
            val c = FlightCalculator()
            for (i in 0..5) c.update(base, i * 1000L)
            c.pause()
            assertNull(c.update(next, 8000).observedEnginePeak)
        }
        for (time in listOf(4000L, 5000L)) {
            val c = FlightCalculator()
            for (i in 0..5) c.update(base, i * 1000L)
            c.pause()
            assertNull(c.update(base, time).observedEnginePeak)
        }
        val c = FlightCalculator()
        for (i in 0..5) c.update(base, i * 1000L)
        c.pause()
        c.reset()
        assertNull(c.update(changed(), 8000).observedEnginePeak)
    }

    @Test fun waitsForFullThrottleAndTracksLargerPeaksWithoutLosingIdleZero() {
        val c = FlightCalculator()
        for (i in 0..4) assertNull(c.update(base, i * 1000L).observedEnginePeak)
        assertEquals(100.0, c.update(base, 5000).observedEnginePeak?.percent)
        assertEquals(50.0, c.update(changed(), 6000).observedEnginePeak?.percent)
        assertEquals(0.0, c.update(changed(0.0), 7000).observedEnginePeak?.percent)
        for (i in 8..13) c.update(changed(2000.0, 100.0), i * 1000L)
        assertEquals(25.0, c.update(changed(), 14000).observedEnginePeak?.percent)
    }

    @Test fun interruptionsMissingChannelsAndEngineChangesResetHistory() {
        for (next in listOf(changed().copy(aircraft = "other"), changed().copy(aircraft = null),
            changed().copy(engines = emptyList()), changed().copy(engines = base.engines + base.engines),
            changed().copy(engines = base.engines.map { it.copy(powerHp = null) }),
            changed().copy(engines = base.engines.map { it.copy(throttlePercent = null) }))) {
            val c = FlightCalculator()
            for (i in 0..5) c.update(base, i * 1000L)
            assertNull(c.update(next, 6000).observedEnginePeak)
        }
        for (time in listOf(5000L, 4000L, 8000L)) {
            val c = FlightCalculator()
            for (i in 0..5) c.update(base, i * 1000L)
            assertNull(c.update(base, time).observedEnginePeak)
        }
        val c = FlightCalculator()
        for (i in 0..5) c.update(base, i * 1000L)
        c.reset()
        assertNull(c.update(base, 6000).observedEnginePeak)
    }

    @Test fun jetRequiresExplicitZeroShaftPowerAndFmTakesPriority() {
        val jet = base.copy(engines = base.engines.map { it.copy(powerHp = 0.0, magneto = null, thrustKgf = 1000.0) })
        val c = FlightCalculator()
        for (i in 0..5) c.update(jet, i * 1000L)
        assertEquals(EnginePeakKind.THRUST_KGF, c.update(jet, 6000).observedEnginePeak?.kind)
        assertNull(c.update(jet.copy(engines = jet.engines.map { it.copy(powerHp = null) }), 7000).observedEnginePeak)
        val flight = ConnectionState.Flying(base, FlightMetrics(observedEnginePeak = ObservedEnginePeak(EnginePeakKind.SHAFT_POWER_HP, 100.0)))
        assertEquals("历史全油门峰值", flight.powerPercentReading(null)?.source)
        val parameters = FlightModelExtractor.extract(BlkParser.parse("Engine0 { Main { Type:t=Inline } }"))
            .copy(enginePeaks = listOf(EnginePeakReference(1, EnginePeakKind.SHAFT_POWER_HP, 2000.0)))
        assertEquals(PowerPercentReading(50.0, "FM 峰值"), flight.powerPercentReading(AircraftAlertModel("test", parameters)))
    }
}
