package voidmei.fm

import kotlin.test.*

class EngineInstanceResolverTest {
    private val base = """
        EngineType0 {
            Main { Type:t=Inline; Power:r=1200; RPMMaxAllowed:r=3200 }
            Compressor { NumSteps:i=1; Altitude0:r=4000; Power0:r=1100 }
        }
    """
    private fun resolve(extra: String) = EngineInstanceResolver.resolve(BlkParser.parse(base + extra))

    @Test fun overridesMergeRecursivelyWithoutChangingTypeOrSiblingInstance() {
        val document = BlkParser.parse(base + """
            Engine0 { Type:i=0; Main { FuelSystemNum:i=1; Power:i=1400 } Compressor { Power0:r=1300 } }
            Engine1 { Type:i=0; Main { FuelSystemNum:i=2 } }
        """)
        val original = document.copy()
        val result = EngineInstanceResolver.resolve(document)
        assertTrue(result.issues.isEmpty())
        val parameters = PistonParameterExtractor.extract(result.document())
        assertTrue(parameters.issues.isEmpty())
        assertEquals(listOf("Engine0", "Engine1"), parameters.engines.map { it.source })
        assertEquals(listOf(1400.0, 1200.0), parameters.engines.map { it.deckPowerHp })
        assertEquals(listOf(1300.0, 1100.0), parameters.engines.map { it.stages.single().powerHp })
        assertEquals(listOf(4000.0, 4000.0), parameters.engines.map { it.stages.single().altitudeM })
        assertEquals(original, document)
        assertEquals(listOf(1.0, 2.0), result.engines.map { it.parameters.fields().single { it.first == "Main.FuelSystemNum" }.second.number() })
    }

    @Test fun ambiguousOverrideInvalidatesOnlyItsInstance() {
        for (bad in listOf("Main { Power:r=1300; power:r=1400 }", "Main:t=broken", "Main { Power:t=broken }")) {
            val result = resolve("Engine0 { Type:i=0; $bad } Engine1 { Type:i=0 }")
            assertEquals(listOf(2), result.engines.map { it.binding.telemetryIndex })
            assertEquals(1, result.issues.size)
        }
    }

    @Test fun duplicateDefaultsAndTypeOnlyDocumentsAreNotInventedInstances() {
        val duplicate = EngineInstanceResolver.resolve(BlkParser.parse(base.replace("Power:r=1200", "Power:r=1200; Power:r=1300") + "Engine0 { Type:i=0 }"))
        assertTrue(duplicate.engines.isEmpty())
        assertTrue(duplicate.issues.single().contains("重复实例参数"))
        assertTrue(resolve("").engines.isEmpty())
    }

    @Test fun inlineAndReferencedJetDefinitionsFeedTheExistingThrustExtractor() {
        val source = FlightModelDocument.parse("""{
            "EngineType0":{"Main":{"Type":"Jet"}},
            "Engine0":{"Type":0,"Main":{"FuelSystemNum":0}},
            "Engine1":{"Main":{"Type":"Inline"},"Compressor":{"NumSteps":1,"Altitude0":0,"Power0":1000}}
        }""")
        val result = EngineInstanceResolver.resolve(source)
        assertEquals(listOf("Jet", "Inline"), result.engines.map { it.binding.type })
        assertEquals("Jet", result.engines.first().parameters.fields().single { it.first == "Main.Type" }.second.values.single())
        assertEquals(listOf("Engine1"), PistonParameterExtractor.extract(result.document()).engines.map { it.source })
    }
}
