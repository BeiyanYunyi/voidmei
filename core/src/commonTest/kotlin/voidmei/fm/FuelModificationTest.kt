package voidmei.fm

import kotlin.test.*

class FuelModificationTest {
    @Test fun listsAllKnownOptionsAndPreservesInvertFlag() {
        val result = FuelModificationExtractor.extract(BlkParser.parse("""
            modifications {
                ussr_fuel_b-95 { effects { addHorsePowers:i=50 } }
                ussr_fuel_b-100 { effects { addHorsePowers:i=50 } }
                150_octan_fuel { invertEnableLogic:b=yes; effects { afterburnerMult:r=1.2; afterburnerCompressorMult:r=1.1 } }
                100_octan_spitfire { invertEnableLogic:b=no; effects { afterburnerMult:r=1.3 } }
                unrelated { effects { broken:t=ignored } }
            }
        """.trimIndent()))
        assertTrue(result.issues.isEmpty())
        assertEquals(4, result.options.size)
        assertTrue(result.options[2].inverted)
        assertFalse(result.options[3].inverted)
        assertNull(result.options[3].compressorMultiplier)
    }

    @Test fun malformedOptionsDoNotSilentlyBecomeNeutralFuel() {
        for (body in listOf("", "effects { addHorsePowers:t=50 }", "effects { addHorsePowers:r=NaN }",
            "effects { addHorsePowers:i=50; addHorsePowers:i=60 }", "effects { addHorsePowers:i=-50 }")) {
            val result = FuelModificationExtractor.extract(BlkParser.parse("modifications { ussr_fuel_b-95 { $body } }"))
            assertTrue(result.options.isEmpty())
            assertEquals(1, result.issues.size)
        }
        val duplicate = FuelModificationExtractor.extract(BlkParser.parse("modifications {} modifications {}"))
        assertTrue(duplicate.options.isEmpty())
        assertTrue(duplicate.issues.isNotEmpty())
        assertTrue(FuelModificationExtractor.extract(BlkParser.parse("fmFile:t=path")).issues.isEmpty())
    }

    private fun raw() = PistonParameterExtractor.extract(BlkParser.parse("""
        EngineType0 {
            Main { Type:t=Inline; Power:r=1200; AfterburnerBoost:r=1.2 }
            Compressor { NumSteps:i=1; Power0:r=1400; Altitude0:r=4000; ATA0:r=1.3; AfterburnerManifoldPressure:r=1.6 }
            Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2400 }
        }
    """.trimIndent())).engines.single()

    @Test fun sovietFuelScalesBaselineOnlyForRecognizedBonus() {
        val fuel = FuelModification("ussr_fuel_b-100", 50.0, null, null, false)
        assertEquals(PistonModelBuilder.build(raw()), PistonModelBuilder.build(raw(), fuel = fuel.copy(inverted = true)))
        val boosted = PistonModelBuilder.build(raw(), fuel = fuel)
        assertEquals(1400 * 1.018, boosted.military.stages.single().critPower, 1e-8)
        assertEquals(1200 * 1.018, boosted.military.stages.single().deckPower, 1e-8)
        assertEquals(1400.0, PistonModelBuilder.build(raw(), fuel = fuel.copy(addedHorsepower = 40.0)).military.stages.single().critPower)
    }

    @Test fun britishFuelReplacesOctaneMultiplierAndRespectsDefaultAndDisabledStages() {
        val fuel = FuelModification("150_octan_fuel", null, 1.5, 1.2, false)
        val base = PistonModelBuilder.build(raw())
        val boosted = PistonModelBuilder.build(raw(), fuel = fuel)
        assertEquals(base.military, boosted.military)
        assertEquals(1.3, boosted.wepStages!!.single().wepPowerMult, 1e-12)
        assertTrue(boosted.wepStages.single().wepCritAlt < base.wepStages!!.single().wepCritAlt)
        assertEquals(base.wepStages.single().wepDeckAlt, boosted.wepStages.single().wepDeckAlt)
        assertEquals(base, PistonModelBuilder.build(raw(), fuel = fuel.copy(inverted = true)))
        val disabled = raw().let { it.copy(stages = it.stages.map { s -> s.copy(afterburnerBoost = 0.0) }) }
        assertFalse(PistonModelBuilder.build(disabled, fuel = fuel).wepStages!!.single().wepEnabled)
    }
}
