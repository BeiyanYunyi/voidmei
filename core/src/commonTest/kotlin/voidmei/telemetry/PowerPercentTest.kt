package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*
import voidmei.config.*

class PowerPercentTest {
    private val document = BlkParser.parse("""
        EngineType0 { Main { Type:t=Jet; AfterburnerBoost:r=2 }
            ThrustMax { ThrustMax0:r=1000; Altitude_0:r=0; Velocity_0:r=0; ThrustMaxCoeff_0_0:r=1 } }
        Engine0 { Type:i=0 }
        Engine3 { Type:i=0; ThrustMax { ThrustMax0:r=2000 } }
    """)
    private val parameters = FlightModelExtractor.extract(document).copy(enginePeaks = EnginePeakExtractor.extract(document))
    private val t = TelemetryParser.parse("""{"valid":true,"thrust 1, kgs":1000,"thrust 4, kgs":2000}""",
        """{"valid":true,"type":"test"}""")!!

    @Test fun instanceOverridesAndSparseIndicesSetAircraftDenominator() {
        assertEquals(listOf(1, 4), parameters.enginePeaks.map { it.telemetryIndex })
        assertEquals(listOf(2000.0, 4000.0), parameters.enginePeaks.map { it.peak })
        assertEquals(50.0, t.powerPercent(parameters))
        assertEquals(50.0, t.copy(engines = t.engines.reversed()).powerPercent(parameters))
        assertEquals(0.0, t.copy(engines = t.engines.map { it.copy(thrustKgf = 0.0) }).powerPercent(parameters))
        assertEquals(100.0, t.copy(engines = t.engines.map { it.copy(thrustKgf = 9000.0) }).powerPercent(parameters))
    }

    @Test fun incompleteAmbiguousOrMixedInputsDoNotProduceAircraftTotal() {
        for (engines in listOf(emptyList(), t.engines.take(1), t.engines + t.engines.first(),
            t.engines.map { it.copy(thrustKgf = null) }, t.engines.map { it.copy(thrustKgf = -1.0) })) {
            assertNull(t.copy(engines = engines).powerPercent(parameters))
        }
        assertNull(t.powerPercent(parameters.copy(enginePeaks = parameters.enginePeaks.take(1))))
        assertNull(t.powerPercent(parameters.copy(enginePeaks = parameters.enginePeaks.mapIndexed { i, peak ->
            if (i == 0) peak.copy(kind = EnginePeakKind.SHAFT_POWER_HP) else peak
        })))
        val flight = ConnectionState.Flying(t, FlightMetrics())
        assertNull(HudField.POWER_PERCENT.value(flight, AircraftAlertModel("other", parameters)))
        assertEquals(50.0, HudField.POWER_PERCENT.value(flight, AircraftAlertModel("test", parameters)))
        val piston = parameters.copy(enginePeaks = parameters.enginePeaks.map { it.copy(kind = EnginePeakKind.SHAFT_POWER_HP) })
        assertEquals(25.0, t.copy(engines = t.engines.map { it.copy(powerHp = 750.0) }).powerPercent(piston))
    }

    @Test fun extractionCancellationPropagatesAndImportPersists() {
        class Cancelled : RuntimeException()
        assertFailsWith<Cancelled> { EnginePeakExtractor.extract(document) { throw Cancelled() } }
        val imported = LegacySettingsReader.read("""(panel p (item power :type data :target getPowerPercent :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("ias")))
        assertEquals(listOf("ias", "power_percent"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
