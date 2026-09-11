package voidmei.recording

import kotlin.test.*

class FlightPerformanceAnalysisTest {
    private val header = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,altitude_m,ias_kmh,total_power_hp,total_thrust_kgf,sep_mps,roll_rate_degps,aileron_percent,load_g,elevator_percent\n"
    private val source = header +
        "0,,5000,test,150,200,1000,400,-2,100,80,8,80\n" +
        "1,,6000,test,200,200,1100,450,-4,130,90,8,90\n" +
        "2,,7000,test,400,220,,,,-200,100,12,100\n" +
        "3,,8000,test,100,200,900,400,3,200,70,15,70\n"

    @Test fun rawRowsProduceSparseClimbAndQualifiedManeuverBins() {
        val result = FlightPerformanceAnalyzer.analyze(source)
        assertEquals(listOf(ClimbSample(100, 0, 1000.0, 400.0, -2.0), ClimbSample(200, 1000, 1100.0, 450.0, -4.0),
            ClimbSample(400, 2000, null, null, null)), result.climb)
        assertEquals(listOf(RollSample(200, 130.0, 90.0), RollSample(220, 200.0, 100.0)), result.roll)
        assertEquals(listOf(TurnSample(200, 6.0, -2.5, 90.0)), result.turn)
        val csv = result.csv()
        assertTrue(csv.contains("climb,400,,2000,,,,,,\n"))
        val turnRow = FlightRecordReader.rows(csv).first { it.first() == "turn" }
        assertEquals(listOf(-2.5, 6.0, 90.0), listOf(6, 8, 9).map { turnRow[it].toDouble() })
        assertFalse(csv.contains("climb,300,"))
    }

    @Test fun absentControlsCannotInventManeuverResultsAndReorderedColumnsWork() {
        val result = FlightPerformanceAnalyzer.analyze("aircraft,elapsed_ms,utc_epoch_ms,sample_id,roll_rate_degps,ias_kmh\ntest,100,,0,100,200\n")
        assertTrue(result.roll.isEmpty()); assertTrue(result.turn.isEmpty()); assertTrue(result.climb.isEmpty())
    }

    @Test fun malformedOrMixedAircraftInputIsRejectedAndWorkIsCancellable() {
        assertFails { FlightPerformanceAnalyzer.analyze(source.replace("130,90", "NaN,90")) }
        assertFails { FlightPerformanceAnalyzer.analyze(source.replace("3,,8000,test", "3,,8000,other")) }
        var calls = 0
        class Stopped : Exception()
        assertFailsWith<Stopped> { FlightPerformanceAnalyzer.analyze(source) { if (++calls == 3) throw Stopped() } }
    }
}
