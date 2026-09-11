package voidmei.recording

import voidmei.telemetry.ConnectionState
import kotlin.math.abs
import kotlin.math.floor

sealed interface PerformanceObservation {
    data class Climb(val altitudeM: Int, val elapsedSeconds: Double, val averageClimbMps: Double) : PerformanceObservation
    data class Roll(val iasKmh: Int, val rateDegps: Double) : PerformanceObservation
    data class Turn(val iasKmh: Int, val loadG: Double, val sepMps: Double) : PerformanceObservation
}

/** Per-recording-segment observations, not certified performance limits or a flight envelope. */
class FlightPerformanceMonitor {
    private var origin: Long? = null
    private var previousTime: Long? = null
    private var initialAltitude: Double? = null
    private var highestStage: Int? = null
    private val roll = DoubleArray(256)
    private val aileron = DoubleArray(256)
    private val load = DoubleArray(256)
    private val elevator = DoubleArray(256)
    private val sep = DoubleArray(256)

    fun update(flight: ConnectionState.Flying, elapsedMs: Long): List<PerformanceObservation> {
        if (elapsedMs < 0 || previousTime?.let { elapsedMs <= it } == true) return emptyList()
        previousTime = elapsedMs
        val t = flight.telemetry
        val result = mutableListOf<PerformanceObservation>()
        val altitude = t.altitudeM?.takeIf { it.isFinite() && it in 0.0..<25600.0 }
        if (altitude == null) {
            origin = null; initialAltitude = null; highestStage = null
        } else {
            val stage = floor(altitude / 100).toInt()
            val previous = highestStage
            if (previous == null) {
                origin = elapsedMs; initialAltitude = altitude; highestStage = stage
            } else if (stage > previous) {
                highestStage = stage
                val seconds = (elapsedMs - origin!!) / 1000.0
                if (seconds > 0) result += PerformanceObservation.Climb(stage * 100, seconds,
                    (altitude - initialAltitude!!) / seconds)
            }
        }
        val speed = t.iasKmh?.takeIf { it.isFinite() && it >= 0 } ?: return result
        val bin = floor(speed / 10 + 0.5).toInt()
        if (bin !in 0..255) return result
        val rollRate = t.rollRateDegPerSecond?.takeIf { it.isFinite() }?.let(::abs)
        val alr = t.aileronPercent?.takeIf { it.isFinite() && abs(it) <= 100 }?.let(::abs)
        if (rollRate != null && alr != null && alr > 5 && rollRate > 10 && alr >= aileron[bin] && rollRate > roll[bin]) {
            if (rollRate - roll[bin] > 40) result += PerformanceObservation.Roll(bin * 10, rollRate)
            roll[bin] = rollRate; aileron[bin] = alr
        }
        val g = t.loadG?.takeIf { it.isFinite() }
        val currentSep = flight.metrics.specificExcessPowerMps?.takeIf { it.isFinite() }
        val elev = t.elevatorPercent?.takeIf { it.isFinite() && abs(it) <= 100 }?.let(::abs)
        if (g != null && currentSep != null && elev != null && g > 1 && currentSep < 5 && elev >= elevator[bin]) {
            val nextLoad = (load[bin] + g) / 2
            val nextSep = (sep[bin] + currentSep) / 2
            if (g - load[bin] > 3) result += PerformanceObservation.Turn(bin * 10, nextLoad, nextSep)
            load[bin] = nextLoad; sep[bin] = nextSep; elevator[bin] = elev
        }
        return result
    }
}
