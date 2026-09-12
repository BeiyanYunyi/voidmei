package voidmei.fm

import kotlin.test.*

class ThermalRecoverySummaryTest {
    private fun band(index: Int, work: Double?, recover: Double?) = EngineThermalBand(index, 100.0, null, work, recover)
    @Test fun averagesOnlyComputableBandsWithoutBorrowingOtherEngineRates() {
        val one = EngineThermalParameters(1, listOf(band(0, null, null), band(1, 120.0, 60.0), band(2, 90.0, 30.0)))
        assertEquals(ThermalRecoverySummary(2, 3, 2.5), one.recoverySummary())
        assertEquals(ThermalRecoverySummary(2, 2, 1.0), EngineThermalParameters(2, listOf(band(0, 0.0, 10.0), band(1, 20.0, 10.0))).recoverySummary())
        assertEquals(ThermalRecoverySummary(0, 0, null), EngineThermalParameters(3, emptyList()).recoverySummary())
    }
    @Test fun missingZeroRecoveryAndOverflowStayUnknownWhileLargeMeanIsFinite() {
        for ((work, recovery) in listOf(null to 10.0, 10.0 to null, 10.0 to 0.0, -1.0 to 10.0,
            Double.NaN to 10.0, 10.0 to Double.POSITIVE_INFINITY, Double.MAX_VALUE to Double.MIN_VALUE)) {
            assertNull(band(0, work, recovery).recoveryRate())
        }
        val large = EngineThermalParameters(1, listOf(band(0, Double.MAX_VALUE, 1.0), band(1, Double.MAX_VALUE, 1.0)))
        assertEquals(Double.MAX_VALUE, large.recoverySummary().meanRate)
    }
}
