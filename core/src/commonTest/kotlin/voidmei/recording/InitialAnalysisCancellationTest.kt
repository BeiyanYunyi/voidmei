package voidmei.recording

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class InitialAnalysisCancellationTest {
    @Test fun engineSummaryStopsBeforeProcessingTheRemainingRows() {
        val text = "sample_id,utc_epoch_ms,engine_index,rpm\n" +
            (0..100).joinToString("\n") { "$it,$it,1,3000" } + "\ninvalid"
        val cancelled = CancellationException("closed")
        var checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            EngineRecordReader.summarize(text) { if (++checks == 10) throw cancelled }
        })
        assertEquals(10, checks)
    }

    @Test fun legacyConversionAndAnalysisPropagateCancellation() {
        val row = MutableList(32) { "0" }.apply { this[31] = "" }.joinToString(",")
        val text = LegacyFlightRecordReader.header + "\n" + List(100) { row }.joinToString("\n") + "\ninvalid"
        for (analyze in listOf(false, true)) {
            val cancelled = CancellationException("closed")
            var checks = 0
            val check = { if (++checks == 10) throw cancelled }
            assertSame(cancelled, assertFailsWith<CancellationException> {
                if (analyze) LegacyFlightRecordReader.analyze(text, check)
                else LegacyFlightRecordReader.normalize(text, check)
            })
            assertEquals(10, checks)
        }
    }
}
