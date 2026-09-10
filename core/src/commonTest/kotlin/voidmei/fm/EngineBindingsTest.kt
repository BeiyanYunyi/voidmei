package voidmei.fm

import kotlin.test.*

class EngineBindingsTest {
    private fun read(text: String) = EngineBindingExtractor.extract(FlightModelDocument.parse(text))

    @Test fun inlineInstancesAndSharedTypesPreserveInstanceNumbers() {
        val direct = read("""{"Engine1":{"Main":{"Type":"Inline"}},"Engine0":{"Main":{"Type":"Inline"}}}""")
        assertEquals(listOf(EngineBinding(1, "Engine0", "Engine0", "Inline"), EngineBinding(2, "Engine1", "Engine1", "Inline")), direct.bindings)
        val referenced = read("""{"EngineType0":{"Main":{"Type":"Jet"}},"Engine0":{"Type":0},"Engine1":{"Type":0}}""")
        assertEquals(listOf(EngineBinding(1, "Engine0", "EngineType0", "Jet"), EngineBinding(2, "Engine1", "EngineType0", "Jet")), referenced.bindings)
        assertTrue(direct.issues.isEmpty())
        assertTrue(referenced.issues.isEmpty())
    }

    @Test fun instanceOverridesRemainExplicitInsteadOfBeingSilentlyMerged() {
        val result = read("""{"EngineType0":{"Main":{"Type":"Jet"}},"Engine0":{"Type":0,"Main":{"FuelSystemNum":0}}}""")
        assertEquals(listOf(EngineBinding(1, "Engine0", "EngineType0", "Jet", true)), result.bindings)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun mixedTypesAndSparseInstancesAreNotRenumbered() {
        val result = read("""
            EngineType4 { Main { Type:t=Jet } }
            EngineType7 { Main { Type:t=Radial } }
            Engine0 { Type:i=7 }
            Engine3 { Type:i=4 }
        """)
        assertEquals(listOf(1, 4), result.bindings.map { it.telemetryIndex })
        assertEquals(listOf("EngineType7", "EngineType4"), result.bindings.map { it.parameterSource })
    }

    @Test fun typeOnlyBlocksDoNotInventEngineInstances() {
        val result = read("EngineType0 { Main { Type:t=Jet } }")
        assertTrue(result.bindings.isEmpty())
        assertTrue(result.issues.single().contains("缺少 EngineN"))
    }

    @Test fun invalidReferencesAndConflictingBlocksDoNotBind() {
        val type = "EngineType0 { Main { Type:t=Jet } }"
        for (instance in listOf("Engine0 { Type:i=1 }", "Engine0 { Type:r=0.5 }",
            "Engine0 { Type:t=0 }", "Engine0 { Type:i=-1 }", "Engine0 { Type:i=0; Type:i=0 }",
            "Engine0 { Type:i=0; Main { Type:t=Inline } }", "Engine0 {}",
            "Engine0 { Main { Type:t=Inline; Type:t=Jet } }",
            "Engine0 { Type:i=0 } Engine00 { Type:i=0 }")) {
            val result = read("$type $instance")
            assertTrue(result.bindings.isEmpty(), instance)
            assertTrue(result.issues.isNotEmpty(), instance)
        }
        val duplicateType = read("$type EngineType00 { Main { Type:t=Jet } } Engine0 { Type:i=0 }")
        assertTrue(duplicateType.bindings.isEmpty())
        assertTrue(duplicateType.issues.single().contains("缺失或重复"))
    }
}
