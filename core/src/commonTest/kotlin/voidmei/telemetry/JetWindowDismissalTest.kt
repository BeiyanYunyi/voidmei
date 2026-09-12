package voidmei.telemetry

import kotlin.test.*

class JetWindowDismissalTest {
    private val sample = TelemetryParser.parse("""{"valid":true,"throttle 1, %":50}""", """{"valid":true}""")!!
    @Test fun gearOrExplicitSpeedAndThrottleTrigger() {
        assertTrue(sample.copy(gearPercent = 99.0, tasKmh = null).triggersJetWindowDismissal())
        assertFalse(sample.copy(gearPercent = 100.0, tasKmh = 36.0).triggersJetWindowDismissal())
        assertTrue(sample.copy(gearPercent = 100.0, tasKmh = 36.01).triggersJetWindowDismissal())
        assertFalse(sample.copy(gearPercent = 100.0, tasKmh = 100.0, engines = sample.engines.map { it.copy(throttlePercent = 0.0) }).triggersJetWindowDismissal())
    }
    @Test fun unknownAndInvalidValuesDoNotTrigger() {
        for (gear in listOf(null, -1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(sample.copy(gearPercent = gear, tasKmh = null).triggersJetWindowDismissal())
        for (speed in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(sample.copy(gearPercent = 100.0, tasKmh = speed).triggersJetWindowDismissal())
        for (throttle in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(sample.copy(gearPercent = 100.0, tasKmh = 100.0, engines = sample.engines.map { it.copy(throttlePercent = throttle) }).triggersJetWindowDismissal())
    }
}
