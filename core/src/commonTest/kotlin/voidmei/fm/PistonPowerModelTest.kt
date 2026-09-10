package voidmei.fm

import kotlin.math.abs
import kotlin.test.*

class PistonPowerModelTest {
    @Test fun agreesWithLegacyJavaAcrossCurveBranchesAndRamConditions() {
        for (line in LegacyPistonReference.samples.lineSequence()) {
            val columns = line.split(',')
            val (index, altitude, wep, speed, eas) = columns
            val expected = columns[5].toDouble()
            val actual = PistonPowerModel.powerAtAltitude(LegacyPistonReference.stages[index.toInt()],
                altitude.toDouble(), wep.toBoolean(), speed.toDouble(), eas.toBoolean())
            if (!expected.isFinite() || expected < 0) assertNull(actual, line)
            else assertEquals(expected, assertNotNull(actual, line), maxOf(1e-7, abs(expected) * 1e-10), line)
        }
    }

    @Test fun knownAnchorsAndStageSelection() {
        val low = CompressorStage(3000.0, 1400.0, 1200.0)
        val high = CompressorStage(6000.0, 1500.0, 900.0)
        assertEquals(1200.0, PistonPowerModel.powerAtAltitude(low, 0.0))
        assertEquals(1400.0, PistonPowerModel.powerAtAltitude(low, 3000.0))
        assertEquals(0, PistonPowerModel.optimalPower(listOf(low, high), 0.0)?.stageIndex)
        assertEquals(1, PistonPowerModel.optimalPower(listOf(low, high), 6000.0)?.stageIndex)
        assertEquals(0, PistonPowerModel.optimalPower(listOf(low, low), 3000.0)?.stageIndex)
        assertNull(PistonPowerModel.optimalPower(emptyList(), 0.0))
    }

    @Test fun ramAndNoBoostInvariants() {
        val stage = CompressorStage(3000.0, 1400.0, 1200.0)
        for (alt in 0..10000 step 1000) {
            assertEquals(PistonPowerModel.powerAtAltitude(stage, alt.toDouble()),
                PistonPowerModel.powerAtAltitude(stage, alt.toDouble(), wep = true))
            assertEquals(PistonPowerModel.powerAtAltitude(stage, alt.toDouble()),
                PistonPowerModel.powerAtAltitude(stage.copy(speedManifoldMult = 0.0), alt.toDouble(), speedKmh = 500.0))
        }
        assertTrue(PistonPowerModel.powerAtAltitude(stage, 7000.0, speedKmh = 450.0)!! >
            PistonPowerModel.powerAtAltitude(stage, 7000.0)!!)
    }

    @Test fun rejectsInvalidInputsWithoutFabricatingPower() {
        val stage = CompressorStage(3000.0, 1400.0, 1200.0)
        for (alt in listOf(Double.NaN, Double.POSITIVE_INFINITY, -4001.0, 20001.0)) {
            assertNull(PistonPowerModel.powerAtAltitude(stage, alt))
        }
        assertNull(PistonPowerModel.powerAtAltitude(stage, 1000.0, speedKmh = -1.0))
        assertNull(PistonPowerModel.powerAtAltitude(stage, 1000.0, seaLevelTempC = -273.15))
        assertFails { stage.copy(curvature = Double.NaN) }
        assertFails { stage.copy(critPower = -100.0) }
        assertFails { PistonPowerModel.curve(listOf(stage), stepM = 0) }
    }

    @Test fun curveIncludesAltitudeAndChosenStage() {
        val stage = CompressorStage(3000.0, 1400.0, 1200.0)
        val curve = PistonPowerModel.curve(listOf(stage), stepM = 1000)
        assertEquals(11, curve.size)
        assertEquals(0.0, curve.first()?.altitudeM)
        assertEquals(10000.0, curve.last()?.altitudeM)
        assertTrue(curve.all { it != null && it.stageIndex == 0 && it.powerHp.isFinite() })
    }
}
