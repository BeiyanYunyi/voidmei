package voidmei.fm

import kotlin.test.*

class CompressorDiagnosticsTest {
    private val base = """
        EngineType0 {
            Main { Type:t=Inline; Power:r=800 }
            Propeller { ThrottleRPMAuto0:p2=1,3000; ThrottleRPMAuto1:p2=1.1,3000 }
            Compressor {
                NumSteps:i=2; Altitude0:r=1000; Power0:r=1000
                Altitude1:r=2000; Power1:r=1200; ExactAltitudes:b=yes
            }
        }
        Engine1 { Type:i=0 }
    """
    private fun extract(extra: String) = FlightModelExtractor.extract(BlkParser.parse(base + extra))

    @Test fun malformedStageReportsCauseAndKeepsSiblingModel() {
        val result = extract("Engine0 { Type:i=0; Compressor { NumSteps:r=2.5 } }")
        assertEquals(setOf(2), result.engineCompressors.keys)
        assertTrue(result.issues.any { it.startsWith("Engine0:") && it.contains("NumSteps") })
        assertTrue(result.issues.none { it.startsWith("Engine1:") })
    }

    @Test fun missingMilitaryRpmReportsBuildFailureWithoutInventingAModel() {
        val result = extract("Engine0 { Type:i=0; Propeller { ThrottleRPMAuto0:p2=0.8,3000 } }")
        assertEquals(setOf(2), result.engineCompressors.keys)
        assertTrue(result.issues.any { it.startsWith("Engine0:") && it.contains("缺少军用转速") })
    }

    @Test fun invalidCurveShapeNamesTheFieldAndPreservesOtherEngines() {
        val result = extract("Engine0 { Type:i=0; Compressor { PowerConstRPMCurvature0:r=-1 } }")
        assertEquals(setOf(2), result.engineCompressors.keys)
        assertTrue(result.issues.any { it.startsWith("Engine0:") && it.contains("PowerConstRPMCurvature") })
    }

    @Test fun missingWepRpmRetainsMilitaryModelAndExplainsPartialAvailability() {
        val result = extract("Engine0 { Type:i=0; Propeller { ThrottleRPMAuto1:p2=0.9,3000 } }")
        assertEquals(setOf(1, 2), result.engineCompressors.keys)
        assertNull(result.engineCompressors.getValue(1).wepStages)
        assertNotNull(result.engineCompressors.getValue(2).wepStages)
        assertTrue(result.issues.any { it.startsWith("Engine0:") && it.contains("WEP") && it.contains("缺少 WEP 转速") })
        assertEquals(result.issues.distinct(), result.issues)
    }

    @Test fun singleStageAndJetDoNotProduceSpuriousShiftModelErrors() {
        val result = FlightModelExtractor.extract(BlkParser.parse("""
            Engine0 { Main { Type:t=Jet } }
            Engine1 { Main { Type:t=Inline }
                Compressor { NumSteps:i=1; Altitude0:r=0; Power0:r=1000 }
            }
        """))
        assertTrue(result.engineCompressors.isEmpty())
        assertTrue(result.issues.isEmpty(), result.issues.toString())
    }
}
