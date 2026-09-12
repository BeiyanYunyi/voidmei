package voidmei.fm

import kotlin.test.*

class ModelWepDurationTest {
    @Test fun sumsEngineConsumptionFromOneSharedTank() {
        assertEquals(240.0, WepFuelModel(120.0, mapOf(1 to .25, 2 to .25, 3 to 0.0)).fullConsumptionDurationSeconds())
        assertEquals(480.0, WepFuelModel(120.0, mapOf(1 to .25)).fullConsumptionDurationSeconds())
        val model = WepFuelExtractor.extract(BlkParser.parse("""
            Mass { MaxNitro:r=120 }
            EngineType0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.25 } }
            Engine0 { Type:i=0 } Engine1 { Type:i=0 }
        """))!!
        assertEquals(240.0, model.fullConsumptionDurationSeconds())
    }
    @Test fun invalidAndOverflowingInputsDoNotInventADeadline() {
        for (model in listOf(WepFuelModel(0.0, mapOf(1 to 1.0)), WepFuelModel(Double.NaN, mapOf(1 to 1.0)),
            WepFuelModel(10.0, emptyMap()), WepFuelModel(10.0, mapOf(1 to 0.0)), WepFuelModel(10.0, mapOf(0 to 1.0)),
            WepFuelModel(10.0, mapOf(1 to -1.0)), WepFuelModel(10.0, mapOf(1 to Double.POSITIVE_INFINITY)),
            WepFuelModel(10.0, mapOf(1 to Double.MAX_VALUE, 2 to Double.MAX_VALUE)),
            WepFuelModel(Double.MAX_VALUE, mapOf(1 to Double.MIN_VALUE)))) assertNull(model.fullConsumptionDurationSeconds())
    }
}
