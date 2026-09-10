package voidmei.fm

import kotlin.math.abs
import kotlin.test.*

class PistonPipelineReferenceTest {
    private fun model(scenario: Int): PistonModels {
        val shaftRpm = if (scenario == 0 || scenario >= 6) 0 else 2700
        val nominalRpm = if (scenario == 6) 2600 else 0
        val governor = if (scenario == 7) 2500 else 0
        val raw = PistonParameterExtractor.extract(BlkParser.parse("""
            EngineType0 {
                Main { Type:t=Inline; Power:r=1200; AfterburnerBoost:r=1.2; ThrottleBoost:r=1.05
                    OctaneAfterburnerMult:r=1.1; ShaftRPMMax:r=$shaftRpm; RPMNom:r=$nominalRpm }
                Compressor {
                    NumSteps:i=2; Altitude0:r=4000; Altitude1:r=6500; Power0:r=1400; Power1:r=1600
                    Ceiling0:r=9000; Ceiling1:r=10000; PowerAtCeiling0:r=600; PowerAtCeiling1:r=700
                    AltitudeConstRPM0:r=1800; AltitudeConstRPM1:r=3000; PowerConstRPM0:r=1300; PowerConstRPM1:r=1450
                    AfterburnerBoostMul1:r=1.02; PowerConstRPMCurvature0:r=1.3; PowerConstRPMCurvature1:r=1.5
                    SpeedManifoldMultiplier:r=0.8; CompressorPressureAtRPM0:r=0.3; CompressorOmegaFactorSq:r=1
                    ExactAltitudes:b=${scenario == 2}; ATA0:r=1.3; ATA1:r=1.4
                    AfterburnerPressureBoost0:r=1.05; AfterburnerPressureBoost1:r=1; AfterburnerManifoldPressure:r=1.7
                }
                Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2700; GovernorMaxParam:r=$governor }
            }
        """.trimIndent())).engines.single()
        val fuel = when (scenario) {
            3 -> FuelModification("ussr_fuel_b-100", 50.0, null, null, false)
            4, 5 -> FuelModification("150_octan_fuel", null, 1.5, 1.2, scenario == 5)
            else -> null
        }
        return PistonModelBuilder.build(raw, fuel = fuel)
    }

    @Test fun stageParametersMatchLegacyRpmAndFuelPipeline() {
        val models = (0..7).map(::model)
        for (line in legacyPipelineSamples.lineSequence()) {
            val columns = line.split(',')
            val scenario = columns[0].toInt()
            val index = columns[1].toInt()
            val result = models[scenario]
            assertNull(result.wepIssue)
            val s = assertNotNull(result.wepStages)[index]
            val values = linkedMapOf("critAlt" to s.critAlt, "critPower" to s.critPower,
                "deckPower" to s.deckPower, "deckAlt" to s.deckAlt, "curvature" to s.curvature,
                "wepCritAlt" to s.wepCritAlt, "wepPowerMult" to s.wepPowerMult, "speedManifoldMult" to s.speedManifoldMult,
                "constRpmAlt" to s.constRpmAlt, "constRpmPower" to s.constRpmPower, "ceilingAlt" to s.ceilingAlt,
                "ceilingPower" to s.ceilingPower, "oldAltitude" to s.oldAltitude, "oldPower" to s.oldPower,
                "oldPowerNewRpm" to s.oldPowerNewRpm, "wepDeckAlt" to s.wepDeckAlt,
                "wepConstRpmAlt" to s.wepConstRpmAlt, "stage0DeckAlt" to s.stage0DeckAlt)
            assertEquals(values.size, columns.size - 2)
            values.entries.forEachIndexed { i, (name, actual) ->
                val expected = columns[i + 2].toDouble()
                assertEquals(expected, actual, maxOf(1e-7, abs(expected) * 1e-10), "scenario=$scenario stage=$index field=$name")
            }
        }
    }
}
