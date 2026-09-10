package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class ManifoldPressureTest {
    @Test fun absolutePressureAndFixedReferenceBoostRemainDistinct() {
        assertEquals(1.0, ManifoldPressureUnit.ATM.fromAtm(1.0))
        assertEquals(29.9213, ManifoldPressureUnit.INHG.fromAtm(1.0)!!, 0.0001)
        assertEquals(0.0, ManifoldPressureUnit.BOOST_PSI.fromAtm(1.0))
        assertEquals(14.69595, ManifoldPressureUnit.BOOST_PSI.fromAtm(2.0)!!, 0.00001)
        assertEquals(-7.3479747, ManifoldPressureUnit.BOOST_PSI.fromAtm(0.5)!!, 0.00001)
        assertEquals(0.0, ManifoldPressureUnit.INHG.fromAtm(0.0))
        for (bad in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            ManifoldPressureUnit.entries.forEach { assertNull(it.fromAtm(bad)) }
        }
        assertNull(ManifoldPressureUnit.INHG.fromAtm(Double.MAX_VALUE))
    }

    @Test fun hudUsesStableEngineOneAndLegacyImportPreservesOtherUnits() {
        val t = TelemetryParser.parse("""{"valid":true,"manifold pressure 1, atm":1,
            "manifold pressure 2, atm":2}""", """{"valid":true}""")!!
        fun flight(engines: List<Engine>) = ConnectionState.Flying(t.copy(engines = engines), FlightMetrics())
        assertEquals(1.0, HudField.ENGINE1_MANIFOLD_ATM.value(flight(t.engines.reversed())))
        for (engines in listOf(t.engines.filter { it.index != 1 }, t.engines + t.engines.first())) {
            for (field in listOf(HudField.ENGINE1_MANIFOLD_ATM, HudField.ENGINE1_MANIFOLD_INHG, HudField.ENGINE1_BOOST_PSI)) {
                assertNull(field.value(flight(engines)))
            }
        }
        val imported = LegacySettingsReader.read("""(panel p
            (item pressure :type data :target getManifoldPressureDisplay :value true))""")
        val result = imported.applyTo(AppSettings(hudFields = listOf("future", "engine1_boost_psi")))
        assertEquals(listOf("future", "engine1_boost_psi", "engine1_manifold_auto"), result.hudFields)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
    }
}
