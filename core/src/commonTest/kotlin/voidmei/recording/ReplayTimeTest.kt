package voidmei.recording

import kotlin.test.*

class ReplayTimeTest {
    @Test fun loopAndStopRespectNonzeroBoundsSpeedAndElapsedRemainder() {
        assertEquals(1750, ReplayTime.position(1000, 2000, 1500, 250.0, 1.0, false))
        assertEquals(2000, ReplayTime.position(1000, 2000, 1500, 5000.0, 1.0, false))
        assertEquals(1250, ReplayTime.position(1000, 2000, 1500, 1750.0, 1.0, true))
        assertEquals(1500, ReplayTime.position(1000, 2000, 1000, 125.0, 4.0, true))
        assertEquals(1000, ReplayTime.position(1000, 1000, 1000, 5000.0, 1.0, true))
        assertEquals(Long.MAX_VALUE, ReplayTime.position(1, Long.MAX_VALUE, 1, Long.MAX_VALUE.toDouble(), 1.0, false))
        assertFails { ReplayTime.position(0, 1000, 0, Double.NaN, 1.0, false) }
        assertFails { ReplayTime.position(0, 1000, 1001, 0.0, 1.0, false) }
    }
}
