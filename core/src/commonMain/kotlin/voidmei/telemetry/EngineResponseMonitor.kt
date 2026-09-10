package voidmei.telemetry

import kotlin.math.exp

/** Signed derivative of displayed power percentage, with a one-second smoothing time constant. */
class EngineResponseMonitor {
    private data class Sample(val aircraft: String, val indices: Set<Int>, val reference: Any,
        val percent: Double, val timeMs: Long)
    private var previous: Sample? = null
    private var smoothed: Double? = null
    fun reset() { previous = null; smoothed = null }

    fun update(state: ConnectionState, model: AircraftAlertModel?, timeMs: Long): ConnectionState {
        val flight = state as? ConnectionState.Flying
        val aircraft = flight?.telemetry?.aircraft
        val reading = flight?.powerPercentReading(model)
        if (flight == null || aircraft == null || reading == null || !reading.percent.isFinite()) {
            reset()
            return flight?.copy(metrics = flight.metrics.copy(engineResponsePercentPerSecond = null)) ?: state
        }
        val parameters = model?.parametersFor(aircraft)
        val fmValue = parameters?.let { flight.telemetry.powerPercent(it) }
        val reference: Any? = if (fmValue != null) parameters!!.enginePeaks.sortedBy { it.telemetryIndex }
            else flight.metrics.observedEnginePeak?.let { peak -> peak.reference?.let { peak.kind to it } }
        if (reference == null) {
            reset()
            return flight.copy(metrics = flight.metrics.copy(engineResponsePercentPerSecond = null))
        }
        val current = Sample(aircraft, flight.telemetry.engines.map { it.index }.toSet(), reference, reading.percent, timeMs)
        val last = previous
        previous = current
        if (last == null || last.aircraft != aircraft || last.indices != current.indices || last.reference != reference ||
            timeMs <= last.timeMs || timeMs - last.timeMs !in 1..2000) {
            smoothed = null
        } else {
            val seconds = (timeMs - last.timeMs) / 1000.0
            val rate = (current.percent - last.percent) / seconds
            // Exact exponential coefficient remains stable when the polling interval changes.
            val alpha = 1 - exp(-seconds)
            smoothed = ((smoothed ?: 0.0) + alpha * (rate - (smoothed ?: 0.0))).takeIf { it.isFinite() }
        }
        return flight.copy(metrics = flight.metrics.copy(engineResponsePercentPerSecond = smoothed))
    }
}
