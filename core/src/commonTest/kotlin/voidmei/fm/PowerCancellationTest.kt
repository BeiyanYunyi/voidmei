package voidmei.fm

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class PowerCancellationTest {
    @Test fun cancellationStopsInsideStageSelectionAndAcrossAltitudes() {
        val stage = CompressorStage(3000.0, 1200.0, 1000.0)
        val stages = List(20) { stage }
        val cancelled = CancellationException("new curve conditions")
        var checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            PistonPowerModel.optimalPower(stages, 1000.0, checkActive = { if (++checks == 5) throw cancelled })
        })
        checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            PistonPowerModel.curve(stages, checkActive = { if (++checks == 30) throw cancelled })
        })
        assertEquals(30, checks)
        assertEquals(PistonPowerModel.curve(stages), PistonPowerModel.curve(stages, checkActive = {}))
    }
}
