package voidmei.desktop

import java.util.Locale
import voidmei.recording.PerformanceObservation

internal fun performanceMessage(observation: PerformanceObservation): String {
    fun Double.display() = String.format(Locale.ROOT, "%.1f", this)
    return when (observation) {
        is PerformanceObservation.Climb -> "已记录 ${observation.altitudeM} m 高度档；用时 ${observation.elapsedSeconds.display()} s，平均爬升 ${observation.averageClimbMps.display()} m/s"
        is PerformanceObservation.Roll -> "${observation.iasKmh} km/h 速度档：记录滚转率 ${observation.rateDegps.display()} °/s"
        is PerformanceObservation.Turn -> "${observation.iasKmh} km/h 速度档：平滑过载 ${observation.loadG.display()} G，SEP ${observation.sepMps.display()} m/s"
    }
}
