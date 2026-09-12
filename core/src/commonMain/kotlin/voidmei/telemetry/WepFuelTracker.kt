package voidmei.telemetry

import voidmei.fm.WepFuelModel

/** Upper bounds, because fuel already used before observation is unknown. */
data class WepFuelEstimate(val maximumRemainingKg: Double, val maximumSecondsAtCurrentRate: Double?)

class WepFuelTracker {
    private var aircraft: String? = null
    private var model: WepFuelModel? = null
    private var previousTime: Long? = null
    private var previousRate = 0.0
    private var remaining = 0.0
    private var samplingInterrupted = false
    fun reset() { aircraft = null; model = null; previousTime = null; previousRate = 0.0; remaining = 0.0; samplingInterrupted = false }

    /** Keep observed consumption, but do not integrate an unknown interval at the previous rate. */
    fun pause() { samplingInterrupted = true }

    fun update(t: Telemetry?, parameters: WepFuelModel?, timeMs: Long): WepFuelEstimate? {
        if (t?.aircraft == null || parameters == null || !parameters.capacityKg.isFinite() || parameters.capacityKg <= 0 ||
            parameters.consumptionKgPerSecond.isEmpty() || parameters.consumptionKgPerSecond.any { (index, rate) -> index <= 0 || !rate.isFinite() || rate < 0 } ||
            parameters.consumptionKgPerSecond.values.none { it > 0 }) { reset(); return null }
        val engines = t.engines
        if (engines.size != parameters.consumptionKgPerSecond.size ||
            engines.map { it.index }.toSet() != parameters.consumptionKgPerSecond.keys ||
            engines.any { it.throttlePercent?.let { value -> value.isFinite() && value >= 0 } != true }) { reset(); return null }
        val rate = engines.sumOf { if (it.throttlePercent!! > 100) parameters.consumptionKgPerSecond.getValue(it.index) else 0.0 }
        if (!rate.isFinite()) { reset(); return null }
        val last = previousTime
        if (aircraft != t.aircraft || model != parameters || last == null || timeMs <= last ||
            (!samplingInterrupted && timeMs - last !in 1..2000)) {
            remaining = parameters.capacityKg
        } else if (!samplingInterrupted) {
            remaining = (remaining - previousRate * ((timeMs - last) / 1000.0)).coerceAtLeast(0.0)
        }
        samplingInterrupted = false
        aircraft = t.aircraft; model = parameters; previousTime = timeMs; previousRate = rate
        return WepFuelEstimate(remaining, if (rate > 0) (remaining / rate).takeIf { it.isFinite() } else null)
    }
}
