package voidmei.telemetry

data class FuelEstimate(val consumptionKgPerMinute: Double? = null, val enduranceSeconds: Double? = null)

/** Time-weighted average over at most 30 seconds, with a 10-second warm-up for integer telemetry.
 * Fuel loss includes leaks and tank jettison; 8111 cannot distinguish those from consumption.
 */
class FuelEstimator {
    private data class Sample(val timeMs: Long, val massKg: Double)
    private val samples = ArrayDeque<Sample>()

    fun reset() = samples.clear()

    fun update(fuelKg: Double?, timeMs: Long): FuelEstimate {
        if (fuelKg == null || !fuelKg.isFinite() || fuelKg < 0) {
            reset()
            return FuelEstimate()
        }
        val previous = samples.lastOrNull()
        if (previous != null && (timeMs <= previous.timeMs || timeMs - previous.timeMs !in 1..2000 || fuelKg > previous.massKg)) reset()
        samples.addLast(Sample(timeMs, fuelKg))
        while (samples.size > 1 && timeMs - samples.first().timeMs > 30000) samples.removeFirst()
        val first = samples.first()
        val durationMs = timeMs - first.timeMs
        if (durationMs < 10000) return FuelEstimate()
        val loss = first.massKg - fuelKg
        if (loss <= 0) return FuelEstimate(consumptionKgPerMinute = 0.0)
        val rate = loss / (durationMs / 1000.0)
        if (!rate.isFinite() || rate <= 0) return FuelEstimate()
        return FuelEstimate((rate * 60).takeIf { it.isFinite() }, (fuelKg / rate).takeIf { it.isFinite() })
    }
}
