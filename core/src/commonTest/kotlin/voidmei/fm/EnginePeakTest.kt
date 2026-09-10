package voidmei.fm

import kotlin.test.*

class EnginePeakTest {
    @Test fun pistonPeakMatchesLegacyJavaAcrossTwelveCurveVariants() {
        LegacyPistonReference.stages.forEachIndexed { index, stage ->
            assertEquals(LegacyPistonReference.peaks[index],
                PistonPowerModel.peakWepPower(listOf(stage))!!, 1e-7)
        }
        assertEquals(LegacyPistonReference.peaks.maxOrNull()!!,
            PistonPowerModel.peakWepPower(LegacyPistonReference.stages)!!, 1e-7)
        assertNull(PistonPowerModel.peakWepPower(emptyList()))
    }

    @Test fun peakSearchCanBeInterruptedInsideItsGrid() {
        class Cancelled : RuntimeException()
        var calls = 0
        assertFailsWith<Cancelled> {
            PistonPowerModel.peakWepPower(LegacyPistonReference.stages) {
                if (++calls == 100) throw Cancelled()
            }
        }
        assertEquals(100, calls)
    }

    @Test fun jetPeakRequiresCompleteSelectedModeAndNeverSubstitutesMilitaryForAfterburner() {
        val model = JetThrustModel("test", listOf(0.0, 1000.0), listOf(0.0, 500.0),
            listOf(listOf(100.0, 200.0), listOf(150.0, 80.0)),
            listOf(listOf(250.0, 240.0), listOf(180.0, 120.0)))
        assertEquals(200.0, model.peakThrust())
        assertEquals(250.0, model.peakThrust(true))
        assertNull(model.copy(afterburnerKgf = null).peakThrust(true))
        assertNull(model.copy(militaryKgf = listOf(listOf(100.0, null), listOf(150.0, 80.0))).peakThrust())
        assertNull(model.copy(militaryKgf = List(2) { List(2) { 0.0 } }).peakThrust())
    }
}
