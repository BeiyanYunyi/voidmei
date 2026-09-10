package voidmei.fm

import kotlin.test.*

class JetThrustModelTest {
    @Test fun explicitVelocityAxisCannotSilentlyUseTheWrongSpeed() {
        val tas = JetThrustExtractor.extract(BlkParser.parse(source().replace(
            "ThrustMax {", "ThrustMax { VelocityType:t=TAS;")))
        assertTrue(tas.issues.isEmpty())
        assertEquals(1000.0, tas.engines.single().thrust(0.0, 0.0))
        for (field in listOf("VelocityType:t=IAS", "VelocityType:t=EAS", "VelocityType:i=1",
            "VelocityType:t=TAS; VelocityType:t=TAS")) {
            val result = JetThrustExtractor.extract(BlkParser.parse(source().replace("ThrustMax {", "ThrustMax { $field;")))
            assertTrue(result.engines.isEmpty(), field)
            assertTrue(result.issues.single().contains("VelocityType"), field)
        }
    }

    private fun source() = """
        EngineType0 {
            Main { Type:t=Jet; AfterburnerBoost:r=1.5; Mode0 { ThrustMult:r=1 }; Mode2 { ThrustMult:r=1.1 } }
            ThrustMax { ThrustMax0:r=1000; Altitude_0:r=0; Altitude_1:r=10000
                Velocity_0:r=0; Velocity_1:r=1000
                ThrustMaxCoeff_0_0:r=1; ThrustMaxCoeff_0_1:r=2
                ThrustMaxCoeff_1_0:r=0.5; ThrustMaxCoeff_1_1:r=1
                ThrAftMaxCoeff_0_0:r=0
            }
        }
    """.trimIndent()

    @Test fun extractsPerEngineThrustAndPreservesZeroAfterburnerCoefficient() {
        val result = JetThrustExtractor.extract(BlkParser.parse(source()))
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        val model = result.engines.single()
        assertEquals(1000.0, model.thrust(0.0, 0.0))
        assertEquals(2000.0, model.thrust(0.0, 1000.0))
        assertEquals(0.0, model.thrust(0.0, 0.0, true))
        assertEquals(3300.0, model.thrust(0.0, 1000.0, true)!!, 1e-8)
    }

    @Test fun interpolatesInsideGridAndRejectsExtrapolation() {
        val model = JetThrustExtractor.extract(BlkParser.parse(source())).engines.single()
        assertEquals(1125.0, model.thrust(5000.0, 500.0))
        assertEquals(750.0, model.thrust(5000.0, 0.0))
        assertNull(model.thrust(-1.0, 0.0))
        assertNull(model.thrust(0.0, 1001.0))
        assertNull(model.thrust(Double.NaN, 0.0))
    }

    @Test fun missingCellDoesNotPoisonExactKnownNodeOrBecomeZero() {
        val model = JetThrustExtractor.extract(BlkParser.parse(source().replace("ThrustMaxCoeff_1_1:r=1", ""))).engines.single()
        assertEquals(1000.0, model.thrust(0.0, 0.0))
        assertEquals(750.0, model.thrust(5000.0, 0.0))
        assertNull(model.thrust(5000.0, 500.0))
        assertNull(model.thrust(10000.0, 1000.0))
    }

    @Test fun malformedAxesAndCoefficientsAreDiagnosed() {
        for (text in listOf(source().replace("Altitude_1:r=10000", "Altitude_1:r=0"),
            source().replace("Velocity_1:r=1000", "Velocity_2:r=1000"),
            source().replace("ThrustMaxCoeff_0_0:r=1", "ThrustMaxCoeff_0_0:r=-1"),
            source().replace("ThrustMax0:r=1000", "ThrustMax0:r=NaN"), source() + source())) {
            val result = JetThrustExtractor.extract(BlkParser.parse(text))
            assertTrue(result.engines.isEmpty())
            assertTrue(result.issues.isNotEmpty())
        }
    }

    @Test fun singleNodeAndMissingAfterburnerAreSupported() {
        val model = JetThrustModel("engine", listOf(0.0), listOf(0.0), listOf(listOf(1000.0)), null)
        assertEquals(1000.0, model.thrust(0.0, 0.0))
        assertNull(model.thrust(0.0, 0.0, true))
        assertNull(model.thrust(1.0, 0.0))
        val noBoost = JetThrustExtractor.extract(BlkParser.parse(source().replace("AfterburnerBoost:r=1.5;", ""))).engines.single()
        assertNull(noBoost.afterburnerKgf)
    }
}
