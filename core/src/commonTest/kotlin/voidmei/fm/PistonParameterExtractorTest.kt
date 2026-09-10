package voidmei.fm

import kotlin.test.*

class PistonParameterExtractorTest {
    @Test fun readsMainRpmTableAndRejectsConflictsAcrossSections() {
        val json = """{"EngineType0":{"Main":{"Type":"Inline","Power":1200,
            "ThrottleRPMAuto0":[1.0,2400],"ThrottleRPMAuto1":[1.1,2700]},
            "Compressor":{"NumSteps":1,"Altitude0":3000,"Power0":1400}}}"""
        val raw = PistonParameterExtractor.extract(FlightModelDocument.parse(json)).engines.single()
        assertEquals(2400.0, raw.militaryRpm)
        assertEquals(2700.0, raw.wepRpm)
        assertEquals(2400.0, PistonModelBuilder.build(raw).military.definitionRpm)
        val conflict = engine().replace("Type:t=\"Inline\";", "Type:t=\"Inline\"; ThrottleRPMAuto0:p2=1.0,2500;")
        val result = PistonParameterExtractor.extract(BlkParser.parse(conflict))
        assertTrue(result.engines.isEmpty())
        assertTrue(result.issues.single().contains("多个转速"))
    }

    private fun engine(name: String = "EngineType0", extra: String = "", prop: String = "Propellor") = """
        $name {
            Main { Type:t="Inline"; Power:r=1200; ShaftRPMMax:r=2700 }
            Compressor {
                NumSteps:i=2; Altitude0:r=3000; Power0:r=1400
                Altitude1:r=6000; Power1:r=1500
                AfterburnerBoostMul0:r=0; ExactAltitudes:b=no
                ATA0:r=1.2; ATA12:r=1.4
                AfterburnerManifoldPressure:r=1.6
                $extra
            }
            $prop { ThrottleRPMAuto21:p2=1.1,2700; ThrottleRPMAuto0:p2=1.0,2400 }
        }
    """.trimIndent()

    @Test fun readsScopedStagesAndPreservesMissingVersusZero() {
        val result = PistonParameterExtractor.extract(BlkParser.parse(engine()))
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        val model = result.engines.single()
        assertEquals(listOf(1400.0, 1500.0), model.stages.map { it.powerHp })
        assertEquals(0.0, model.stages[0].afterburnerBoost)
        assertNull(model.stages[1].afterburnerBoost)
        assertNull(model.omegaFactorSquared)
        assertEquals(false, model.exactAltitudes)
        assertEquals(2400.0, model.militaryRpm)
        assertEquals(2700.0, model.wepRpm)
        assertEquals(listOf(1.2, 1.4), model.manifoldPressures)
        assertEquals(1.6, model.wepManifoldPressure)
    }

    @Test fun enginesNeverBorrowEachOthersParameters() {
        val first = engine().replace("Power0:r=1400", "Power0:r=900")
        val second = engine("EngineType1", prop = "Propeller").replace("Power:r=1200", "Power:r=2000")
        val result = PistonParameterExtractor.extract(BlkParser.parse(first + second))
        assertTrue(result.issues.isEmpty())
        assertEquals(listOf(900.0, 1400.0), result.engines.map { it.stages[0].powerHp })
        assertEquals(listOf(1200.0, 2000.0), result.engines.map { it.deckPowerHp })
    }

    @Test fun malformedAndAmbiguousDataCannotProducePartialEngine() {
        val variants = listOf(
            engine(extra = "Power0:r=1300"),
            engine().replace("Power0:r=1400", "Power0:t=oops"),
            engine().replace("Power1:r=1500", ""),
            engine().replace("NumSteps:i=2", "NumSteps:r=2.5"),
            engine().replace("NumSteps:i=2", "NumSteps:i=999999"),
            engine().replace("ShaftRPMMax:r=2700", "ShaftRPMMax:r=NaN"),
            engine().replace("ExactAltitudes:b=no", "ExactAltitudes:t=no"),
            engine().replace("Power:r=1200", "Power:r=1200; AfterburnerManifoldPressure:r=2"),
            engine() + engine(),
        )
        for (text in variants) {
            val result = PistonParameterExtractor.extract(BlkParser.parse(text))
            assertTrue(result.engines.isEmpty(), text)
            assertTrue(result.issues.isNotEmpty(), text)
        }
    }

    @Test fun missingRpmIsNotInventedAndConflictingRpmIsRejected() {
        val missing = engine().replace("ThrottleRPMAuto21:p2=1.1,2700;", "")
        assertNull(PistonParameterExtractor.extract(BlkParser.parse(missing)).engines.single().wepRpm)
        val conflict = engine().replace("ThrottleRPMAuto21:p2=1.1,2700;", "ThrottleRPMAuto21:p2=1.0,2700;")
        assertTrue(PistonParameterExtractor.extract(BlkParser.parse(conflict)).engines.isEmpty())
    }

    @Test fun unsupportedEngineDoesNotHideUsablePistonEngine() {
        val result = PistonParameterExtractor.extract(BlkParser.parse(
            engine() + "EngineType1 { Main { Type:t=Jet } }" + "EngineType2 { Main { Type:t=Unknown } }"))
        assertEquals(1, result.engines.size)
        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single().contains("Unknown"))
    }
}
