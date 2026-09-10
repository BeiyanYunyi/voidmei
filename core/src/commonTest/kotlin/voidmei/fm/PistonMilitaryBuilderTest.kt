package voidmei.fm

import kotlin.test.*

class PistonMilitaryBuilderTest {
    private fun raw() = PistonParameterExtractor.extract(BlkParser.parse("""
        EngineType0 {
            Main { Type:t=Inline; Power:r=1200 }
            Compressor { NumSteps:i=2; Altitude0:r=3000; Power0:r=1400; Altitude1:r=6000; Power1:r=1500; ATA0:r=1.3 }
            Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2700 }
        }
    """.trimIndent())).engines.single()

    @Test fun createsMilitaryBaselineAndCascadesDeckPower() {
        val model = PistonMilitaryBuilder.build(raw())
        assertEquals(2400.0, model.definitionRpm)
        assertEquals(listOf(1200.0, 1200.0), model.stages.map { it.deckPower })
        assertEquals(listOf(1400.0, 1500.0), model.stages.map { it.critPower })
        assertEquals(1200.0, PistonPowerModel.optimalPower(model.stages, 0.0)?.powerHp)
        assertEquals(1500.0, PistonPowerModel.optimalPower(model.stages, 6000.0)?.powerHp)
    }

    @Test fun fuelMultiplierAppliesBeforeRpmAndDeckCascade() {
        val raw = raw().copy(shaftRpmMax = 2700.0)
        val base = PistonMilitaryBuilder.build(raw)
        val boosted = PistonMilitaryBuilder.build(raw, 1.018)
        assertEquals(2700.0, base.definitionRpm)
        for (i in base.stages.indices) {
            assertEquals(base.stages[i].critPower * 1.018, boosted.stages[i].critPower, 1e-8)
            assertEquals(base.stages[i].deckPower * 1.018, boosted.stages[i].deckPower, 1e-8)
            assertEquals(base.stages[i].critAlt, boosted.stages[i].critAlt)
            assertTrue(base.stages[i].critAlt < raw.stages[i].altitudeM)
            assertTrue(base.stages[i].oldPowerNewRpm < base.stages[i].oldPower)
            assertEquals(base.stages[0].deckAlt, base.stages[i].stage0DeckAlt)
        }
        assertEquals(1400.0, raw.stages[0].powerHp) // Source parameters stay immutable.
    }

    @Test fun definitionRpmUsesLegacyPriorityAndFiveRpmThreshold() {
        val raw = raw().copy(shaftRpmMax = 2700.0, rpmNominal = 2600.0, governorMax = 2500.0)
        assertEquals(2700.0, PistonMilitaryBuilder.build(raw).definitionRpm)
        assertEquals(2600.0, PistonMilitaryBuilder.build(raw.copy(shaftRpmMax = null)).definitionRpm)
        assertEquals(2500.0, PistonMilitaryBuilder.build(raw.copy(shaftRpmMax = null, rpmNominal = null)).definitionRpm)
        assertEquals(2400.0, PistonMilitaryBuilder.build(raw.copy(shaftRpmMax = 2405.0, rpmNominal = null, governorMax = null)).definitionRpm)
    }

    @Test fun incompleteInputsDoNotProduceAnAdjustedModel() {
        val raw = raw()
        assertFails { PistonMilitaryBuilder.build(raw.copy(militaryRpm = null)) }
        assertFails { PistonMilitaryBuilder.build(raw.copy(shaftRpmMax = 2700.0, manifoldPressures = emptyList())) }
        assertFails { PistonMilitaryBuilder.build(raw.copy(stages = listOf(raw.stages[0].copy(ceilingM = 9000.0)))) }
        assertFails { PistonMilitaryBuilder.build(raw, Double.NaN) }
        assertFails { PistonMilitaryBuilder.build(raw.copy(stages = emptyList())) }
    }
}
