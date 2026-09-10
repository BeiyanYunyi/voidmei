package voidmei.recording

import kotlin.test.*
import kotlin.coroutines.cancellation.CancellationException

class RecordCancellationTest {
    private val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n" +
        (0..100).joinToString("\n", postfix = "\n") { "$it,${1000 + it},$it,test,300" }
    private val fields = listOf("ias_kmh")

    @Test fun replayCanCancelDuringBothPassesAndDoesNotRetainTheConstructionCallback() {
        val cancelled = CancellationException("cancel replay")
        assertSame(cancelled, assertFailsWith<CancellationException> { RecordedReplay(text, fields) { throw cancelled } })
        var validationChecks = 0
        FlightRecordReader.summarize(text, fields) { validationChecks++ }
        var checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            RecordedReplay(text, fields) { if (++checks > validationChecks + 5) throw cancelled }
        })
        var active = true
        val replay = RecordedReplay(text, fields) { if (!active) throw cancelled }
        active = false
        assertEquals(101, replay.size)
        assertEquals(70, replay.indexAt(70))
        assertEquals(70L, replay.frame(70).sampleId)
        assertEquals(300.0, replay.frame(70).values["ias_kmh"])
    }

    @Test fun cancellationPropagatesFromValidationCroppingAndPlotting() {
        val cancelled = CancellationException("test cancellation")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            FlightRecordReader.summarize(text, fields) { throw cancelled }
        })
        var validationChecks = 0
        FlightRecordReader.summarize(text, fields) { validationChecks++ }
        var checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            FlightRecordWindow.analyze(text, 0, 100, fields) {
                if (++checks > validationChecks + 5) throw cancelled
            }
        })
        checks = 0
        assertSame(cancelled, assertFailsWith<CancellationException> {
            FlightRecordPlots.analyze(text, fields = fields) {
                if (++checks > validationChecks + 5) throw cancelled
            }
        })
        assertEquals(FlightRecordWindow.analyze(text, 20, 80, fields),
            FlightRecordWindow.analyze(text, 20, 80, fields) { })
    }

    @Test fun longQuotedCellsCheckCancellationBeforeTheRowFinishes() {
        val source = "\"" + "a".repeat(100_000) + "\"\n"
        var checks = 0
        assertFailsWith<CancellationException> {
            FlightRecordReader.rows(source) {
                if (++checks == 3) throw CancellationException("long cell")
            }.toList()
        }
        assertEquals(3, checks)
    }
}
