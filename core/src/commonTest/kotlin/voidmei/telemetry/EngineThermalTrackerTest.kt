package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class EngineThermalTrackerTest {
    private val bands = listOf(
        EngineThermalBand(0, 80.0, 60.0, null, null),
        EngineThermalBand(1, 100.0, 90.0, 10.0, 5.0),
        EngineThermalBand(2, 120.0, 110.0, 4.0, 2.0))
    private val models = listOf(EngineThermalParameters(1, bands), EngineThermalParameters(2, bands))
    private fun engine(index: Int = 1, water: Double? = 120.0, oil: Double? = 80.0) =
        Engine(index, null, null, null, null, water, oil)
    private fun List<EngineThermalBudget>.water(index: Int = 1) = single { it.telemetryIndex == index }.water!!

    @Test fun interruptedRangesContainEveryHeatingAndCoolingSequence() {
        // Start from fully recovered budgets, then compare uncertain sampling with all
        // sixteen hot/cold combinations over a two-second gap in the same FM model.
        fun prepared(): EngineThermalTracker = EngineThermalTracker().also { tracker ->
            for (time in 0L..6000L step 2000L) tracker.update("test", models, listOf(engine(water = 50.0, oil = 50.0)), time)
        }
        val interrupted = prepared()
        interrupted.pause()
        val bounds = interrupted.update("test", models, listOf(engine()), 8000).first()
        assertEquals(ThermalBudgetRange(8.0, 10.0), bounds.water!!.bands.first().remaining)
        assertEquals(ThermalBudgetRange(2.0, 4.0), bounds.water!!.bands.last().remaining)
        for (sequence in 0..15) {
            val observed = prepared()
            for (step in 0..3) {
                val hot = sequence and (1 shl step) != 0
                observed.update("test", models, listOf(engine(water = if (hot) 130.0 else 50.0,
                    oil = if (hot) 50.0 else 130.0)), 6000L + step * 500)
            }
            val actual = observed.update("test", models, listOf(engine()), 8000).first()
            for ((range, outcome) in listOf(bounds.water!! to actual.water!!, bounds.oil!! to actual.oil!!)) {
                range.bands.zip(outcome.bands).forEach { (possible, concrete) ->
                    assertTrue(possible.remaining.minimumSeconds <= concrete.remaining.minimumSeconds)
                    assertTrue(possible.remaining.maximumSeconds >= concrete.remaining.maximumSeconds)
                }
            }
        }
    }

    @Test fun interruptedSamplesCannotInventRecoveryWithoutARecoveryRate() {
        for (recovery in listOf(null, 0.0)) {
            val tracker = EngineThermalTracker()
            val parameters = listOf(EngineThermalParameters(1, listOf(bands[2].copy(recoverSeconds = recovery))))
            for (time in 0L..4000L step 2000L) tracker.update("test", parameters, listOf(engine()), time)
            tracker.pause()
            assertEquals(ThermalBudgetRange(0.0, 0.0),
                tracker.update("test", parameters, listOf(engine()), 6000).water().activeRemaining)
        }
    }

    @Test fun budgetsAreIndependentAndInitialWearIsUnknown() {
        val tracker = EngineThermalTracker()
        val engines = listOf(engine(), engine(2, 90.0, 110.0))
        val initial = tracker.update("test", models, engines, 0)
        assertEquals(ThermalBudgetRange(0.0, 4.0), initial.water().activeRemaining)
        assertNull(initial.water(2).activeRemaining)
        val next = tracker.update("test", models, engines, 2000)
        assertEquals(ThermalBudgetRange(0.0, 2.0), next.water().activeRemaining)
        assertEquals(ThermalBudgetRange(4.0, 10.0), next.water(2).bands.first().remaining)
        assertEquals(ThermalBudgetRange(0.0, 2.0), next[1].oil!!.activeRemaining)
        assertEquals(listOf(1, 2), next.water().bands.map { it.index })
    }

    @Test fun exhaustionAndRecoveryClampAndCrossingsUsePreviousSample() {
        val tracker = EngineThermalTracker()
        fun tick(time: Long, temperature: Double) = tracker.update("test", models, listOf(engine(water = temperature)), time).water()
        tick(0, 120.0)
        tick(2000, 120.0)
        assertEquals(ThermalBudgetRange(0.0, 0.0), tick(4000, 120.0).activeRemaining)
        // The preceding interval was hot; the newly cool sample does not refill it retroactively.
        val cooled = tick(6000, 90.0)
        assertNull(cooled.activeRemaining)
        assertEquals(ThermalBudgetRange(0.0, 0.0), cooled.bands.last().remaining)
        assertEquals(ThermalBudgetRange(4.0, 4.0), tick(8000, 90.0).bands.last().remaining)
        tick(10000, 90.0)
        assertEquals(ThermalBudgetRange(10.0, 10.0), tick(12000, 90.0).bands.first().remaining)
        assertEquals(ThermalBudgetRange(4.0, 4.0), tick(12000, 120.0).activeRemaining)
    }

    @Test fun missingChannelDoesNotEraseOtherChannelAndReturnDoesNotChargeUnobservedTime() {
        val tracker = EngineThermalTracker()
        tracker.update("test", models, listOf(engine(oil = 110.0)), 0)
        val missing = tracker.update("test", models, listOf(engine(water = null, oil = 110.0)), 2000)
        assertNull(missing.first().water)
        assertEquals(ThermalBudgetRange(0.0, 2.0), missing.first().oil!!.activeRemaining)
        val returned = tracker.update("test", models, listOf(engine(oil = 110.0)), 4000)
        assertEquals(ThermalBudgetRange(0.0, 4.0), returned.water().activeRemaining)
        assertEquals(ThermalBudgetRange(0.0, 0.0), returned.first().oil!!.activeRemaining)
    }

    @Test fun gapsClockReversalAircraftModelChangesAndResetInvalidateHistory() {
        val tracker = EngineThermalTracker()
        fun tick(time: Long, aircraft: String = "test", parameters: List<EngineThermalParameters> = models) =
            tracker.update(aircraft, parameters, listOf(engine()), time).water().activeRemaining
        tick(0)
        assertEquals(ThermalBudgetRange(0.0, 2.0), tick(2000, "TEST"))
        assertEquals(ThermalBudgetRange(0.0, 4.0), tick(5001))
        tick(7001)
        assertEquals(ThermalBudgetRange(0.0, 4.0), tick(100))
        tick(2100)
        assertEquals(ThermalBudgetRange(0.0, 4.0), tick(4100, "another"))
        tick(6100, "another")
        assertEquals(ThermalBudgetRange(0.0, 4.0), tick(8100, "another", models.take(1)))
        tracker.reset()
        assertEquals(ThermalBudgetRange(0.0, 4.0), tick(10100))
    }

    @Test fun duplicateAndInvalidInputsCannotProduceConfidentBudgets() {
        val tracker = EngineThermalTracker()
        assertTrue(tracker.update("test", models + models, listOf(engine()), 0).isEmpty())
        assertNull(tracker.update("test", models, listOf(engine(), engine()), 1000).first().water)
        for (temperature in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -300.0)) {
            assertNull(tracker.update("test", models, listOf(engine(water = temperature)), 2000).first().water)
        }
        val malformed = listOf(EngineThermalParameters(1, listOf(bands[1].copy(workSeconds = Double.NaN))))
        assertTrue(tracker.update("test", malformed, listOf(engine()), 3000).isEmpty())
    }

    @Test fun missingAndZeroRecoveryDoNotRefillAndZeroWorkIsNotInstantExhaustion() {
        for (recovery in listOf(null, 0.0)) {
            val tracker = EngineThermalTracker()
            val parameters = listOf(EngineThermalParameters(1, listOf(
                bands[1].copy(workSeconds = 0.0), bands[2].copy(recoverSeconds = recovery))))
            tracker.update("test", parameters, listOf(engine()), 0)
            tracker.update("test", parameters, listOf(engine(water = 90.0)), 2000)
            val result = tracker.update("test", parameters, listOf(engine(water = 90.0)), 4000).water()
            assertEquals(1, result.bands.size)
            assertEquals(ThermalBudgetRange(0.0, 2.0), result.bands.single().remaining)
        }
    }
}
