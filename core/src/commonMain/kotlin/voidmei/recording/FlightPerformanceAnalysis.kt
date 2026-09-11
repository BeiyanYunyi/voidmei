package voidmei.recording

import kotlin.math.abs
import kotlin.math.floor

data class ClimbSample(val altitudeM: Int, val elapsedMs: Long, val powerHp: Double?, val thrustKgf: Double?, val sepMps: Double?)
data class RollSample(val iasKmh: Int, val rateDegps: Double, val aileronPercent: Double)
data class TurnSample(val iasKmh: Int, val loadG: Double, val sepMps: Double, val elevatorPercent: Double)
data class FlightPerformanceAnalysis(val climb: List<ClimbSample>, val roll: List<RollSample>, val turn: List<TurnSample>) {
    fun csv(): String = buildString {
        appendLine("kind,altitude_m,ias_kmh,elapsed_ms,power_hp,thrust_kgf,sep_mps,roll_rate_degps,load_g,control_percent")
        fun row(vararg cells: Any?) { appendLine(cells.joinToString(",") { it?.toString().orEmpty() }) }
        climb.forEach { row("climb", it.altitudeM, null, it.elapsedMs, it.powerHp, it.thrustKgf, it.sepMps, null, null, null) }
        roll.forEach { row("roll", null, it.iasKmh, null, null, null, null, it.rateDegps, null, it.aileronPercent) }
        turn.forEach { row("turn", null, it.iasKmh, null, null, null, it.sepMps, null, it.loadG, it.elevatorPercent) }
    }
}

/** Analyze raw samples rather than the time-chart's downsampled points. */
object FlightPerformanceAnalyzer {
    private val fields = listOf("altitude_m", "ias_kmh", "total_power_hp", "total_thrust_kgf", "sep_mps",
        "roll_rate_degps", "aileron_percent", "load_g", "elevator_percent")

    fun analyze(text: String, checkActive: () -> Unit = {}): FlightPerformanceAnalysis {
        FlightRecordReader.summarize(text, fields, checkActive)
        val rows = FlightRecordReader.rows(text.removePrefix("\uFEFF"), checkActive).iterator()
        val columns = rows.next().withIndex().associate { it.value to it.index }
        val climb = mutableMapOf<Int, ClimbSample>()
        val roll = arrayOfNulls<RollSample>(256)
        val turn = arrayOfNulls<TurnSample>(256)
        var highest = -1
        var origin: Long? = null
        while (rows.hasNext()) {
            checkActive()
            val row = rows.next()
            fun number(name: String) = columns[name]?.let { row[it].toDoubleOrNull() }
            val elapsed = row[columns.getValue("elapsed_ms")].toLong()
            if (origin == null) origin = elapsed
            val altitude = number("altitude_m")?.takeIf { it in 0.0..<25600.0 }
            if (altitude != null) {
                val stage = floor(altitude / 100).toInt()
                if (stage > highest) {
                    highest = stage
                    climb[stage] = ClimbSample(stage * 100, elapsed - origin, number("total_power_hp"),
                        number("total_thrust_kgf"), number("sep_mps"))
                }
            }
            val speed = number("ias_kmh")?.takeIf { it >= 0 } ?: continue
            val bin = floor(speed / 10 + 0.5).toInt()
            if (bin !in 0..255) continue
            val rate = number("roll_rate_degps")?.let(::abs)
            val alr = number("aileron_percent")?.let(::abs)?.takeIf { it <= 100 }
            val oldRoll = roll[bin]
            if (rate != null && alr != null && alr > 5 && rate > 10 &&
                alr >= (oldRoll?.aileronPercent ?: 0.0) && rate > (oldRoll?.rateDegps ?: 0.0))
                roll[bin] = RollSample(bin * 10, rate, alr)
            val g = number("load_g")
            val sep = number("sep_mps")
            val elev = number("elevator_percent")?.let(::abs)?.takeIf { it <= 100 }
            val oldTurn = turn[bin]
            if (g != null && sep != null && elev != null && g > 1 && sep < 5 && elev >= (oldTurn?.elevatorPercent ?: 0.0))
                turn[bin] = TurnSample(bin * 10, (oldTurn?.loadG ?: 0.0) / 2 + g / 2,
                    (oldTurn?.sepMps ?: 0.0) / 2 + sep / 2, elev)
        }
        return FlightPerformanceAnalysis(climb.values.sortedBy { it.altitudeM }, roll.filterNotNull(), turn.filterNotNull())
    }
}
