package voidmei.fm

import kotlin.test.*

class MaximumLiftLoadTest {
    private val model = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0), StallLiftProfile(1.0, 5.0, 10.0)))
    @Test fun usesDynamicPressureMassAndMatchingSweepAndFlaps() {
        val expected = .5 * 1.225 * (350.0 / 3.6) * (350.0 / 3.6) * 10.0 / (1000.0 * 9.80)
        assertEquals(expected, model.maximumLiftLoadAtIas(350.0, 0.0, 0.0, 0.0)!!, 1e-10)
        assertEquals(expected * 2, model.maximumLiftLoadAtIas(350.0, 0.0, 100.0, 0.0)!!, 1e-10)
        assertEquals(expected / 2, model.maximumLiftLoadAtIas(350.0, 1000.0, 0.0, 0.0)!!, 1e-10)
        assertEquals(expected * .75, model.maximumLiftLoadAtIas(350.0, 0.0, 0.0, .5)!!, 1e-10)
        assertEquals(1.0, model.maximumLiftLoadAtIas(model.speedKmh(0.0, 0.0, 0.0), 0.0, 0.0, 0.0)!!, 1e-10)
        assertEquals(0.0, model.maximumLiftLoadAtIas(0.0, 0.0, 0.0, 0.0))
    }
    @Test fun unknownConditionsAndOverflowStayUnknown() {
        for (speed in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
            assertNull(model.maximumLiftLoadAtIas(speed, 0.0, 0.0, 0.0))
        assertNull(model.maximumLiftLoadAtIas(350.0, null, 0.0, 0.0))
        assertNull(model.maximumLiftLoadAtIas(350.0, -1.0, 0.0, 0.0))
        assertNull(model.maximumLiftLoadAtIas(350.0, 0.0, null, 0.0))
        assertNull(model.maximumLiftLoadAtIas(350.0, 0.0, 0.0, null))
    }
}
