package voidmei.fm

import kotlin.test.*

class PistonModelBuilderTest {
    private fun raw() = PistonParameterExtractor.extract(BlkParser.parse("""
        EngineType0 {
            Main { Type:t=Inline; Power:r=1200; AfterburnerBoost:r=1.2 }
            Compressor { NumSteps:i=1; Altitude0:r=4000; Power0:r=1400; ATA0:r=1.3; AfterburnerManifoldPressure:r=1.6 }
            Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2400 }
        }
    """.trimIndent())).engines.single()

    @Test fun buildsBoostAndPressureShiftFromRawParameters() {
        val model = PistonModelBuilder.build(raw())
        assertNull(model.wepIssue)
        val stage = assertNotNull(model.wepStages).single()
        assertEquals(1.2, stage.wepPowerMult)
        assertTrue(stage.wepCritAlt < stage.critAlt)
        assertTrue(stage.wepDeckAlt < stage.deckAlt)
        assertTrue(PistonPowerModel.powerAtAltitude(stage, 0.0, wep = true)!! >
            PistonPowerModel.powerAtAltitude(stage, 0.0)!!)
    }

    @Test fun missingWepDataPreservesMilitaryModel() {
        for (raw in listOf(raw().copy(wepRpm = null), raw().copy(wepManifoldPressure = null))) {
            val model = PistonModelBuilder.build(raw)
            assertEquals(1400.0, model.military.stages.single().critPower)
            assertNull(model.wepStages)
            assertNotNull(model.wepIssue)
        }
    }

    @Test fun disabledStageUsesMilitaryBranchesAtEveryAltitude() {
        val raw = raw().copy(shaftRpmMax = 2700.0, wepRpm = 2700.0)
        val disabled = raw.copy(stages = raw.stages.map { it.copy(afterburnerBoost = 0.0) }, wepManifoldPressure = null)
        val model = PistonModelBuilder.build(disabled)
        assertNull(model.wepIssue)
        val stage = model.wepStages!!.single()
        assertFalse(stage.wepEnabled)
        for (altitude in -500..10000 step 100) {
            assertEquals(PistonPowerModel.powerAtAltitude(stage, altitude.toDouble()),
                PistonPowerModel.powerAtAltitude(stage, altitude.toDouble(), wep = true))
        }
    }

    @Test fun explicitZeroCompressorExponentIsNotReplacedByDefault() {
        val raw = raw().copy(wepRpm = 2700.0)
        val defaultStage = PistonModelBuilder.build(raw).wepStages!!.single()
        val zeroStage = PistonModelBuilder.build(raw.copy(omegaFactorSquared = 0.0)).wepStages!!.single()
        assertTrue(zeroStage.wepCritAlt < defaultStage.wepCritAlt)
    }

    @Test fun nonExactConstRpmAltitudeShiftsIndependently() {
        val raw = raw().let { it.copy(exactAltitudes = false,
            stages = it.stages.map { s -> s.copy(constRpmAltitudeM = 1800.0, constRpmPowerHp = 1300.0) }) }
        val stage = PistonModelBuilder.build(raw).wepStages!!.single()
        assertTrue(stage.wepConstRpmAlt < stage.constRpmAlt)
        assertTrue(stage.wepConstRpmAlt.isFinite())
    }
}
