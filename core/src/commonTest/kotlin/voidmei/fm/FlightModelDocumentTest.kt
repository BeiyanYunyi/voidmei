package voidmei.fm

import kotlin.test.*

class FlightModelDocumentTest {
    @Test fun jsonScalarsVectorsAndRepeatedBlocksReachExistingModelExtractors() {
        val doc = FlightModelDocument.parse("""{
            "EmptyMass": 2500, "Vne": 800.5, "FlapsDestructionIndSpeedP": [0.5, 500, 1, 300],
            "Engine0": {"Main": {"Type":"Inline", "Power":1415.0},
                "Compressor": {"NumSteps":1, "Altitude0":7500.0, "Power0":1290.0, "ExactAltitudes":true},
                "Propellor": {"ThrottleRPMAuto0":[1.0,3000.0], "ThrottleRPMAuto1":[1.1,3005.0]}},
            "Repeated": [{"value":1}, {"value":2}], "unused": null,
            "labels": ["a", "b"], "nested": [[1,2],[3,4]]
        }""")
        val model = FlightModelExtractor.extract(doc)
        assertEquals(2500.0, model.emptyMassKg)
        assertEquals(800.5, model.wings.single().vneKmh)
        assertEquals(400.0, model.flapLimits!!.speedAt(75.0))
        val engine = PistonParameterExtractor.extract(doc).engines.single()
        assertEquals(3000.0, engine.militaryRpm)
        assertEquals(3005.0, engine.wepRpm)
        assertEquals(true, engine.exactAltitudes)
        assertEquals(2, doc.entries.filterIsInstance<BlkBlock>().count { it.name == "Repeated" })
        assertEquals("null", doc.field("unused")!!.type)
        assertEquals("json", doc.field("nested")!!.type)
    }

    @Test fun duplicateKeysRemainVisibleToAmbiguityChecks() {
        val doc = FlightModelDocument.parse("""{"EmptyMass":1000,"EmptyMass":2000,"Engine0":{},"Engine0":{}}""")
        assertEquals(2, doc.fields().count { it.first == "EmptyMass" })
        assertNull(FlightModelExtractor.extract(doc).emptyMassKg)
        assertTrue(PistonParameterExtractor.extract(doc).issues.any { it.contains("重复") })
    }

    @Test fun strictJsonAndStructureLimitsRejectMalformedExports() {
        for (text in listOf("{}", "{\"a\":1,}", "{\"a\":[1,]}", "{\"a\":01}", "{\"a\":1e999}",
            "{\"a\":truefalse}", "{\"a\":\"\\x\"}", "{\"a\":1} trailing", "{\"a\":NaN}",
            "{\"a\":".repeat(70) + "0" + "}".repeat(70))) {
            assertFails(text) { FlightModelDocument.parse(text) }
        }
        val typed = "EmptyMass:r=1000"
        assertEquals(BlkParser.parse(typed), FlightModelDocument.parse("\uFEFF$typed"))
        assertEquals("a\nb", FlightModelDocument.parse("""{"name":"a\nb"}""").field("name")!!.values.single())
    }
}
