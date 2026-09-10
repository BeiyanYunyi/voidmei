package voidmei.telemetry

/** One-second least-squares slopes suppress repeated/quantized API speed steps. */
internal class SpeedTrend {
    private data class Sample(val timeMs: Long, val speed: Double)
    private val samples = ArrayDeque<Sample>()

    fun reset() = samples.clear()

    // Caller resets on non-monotonic time, gaps, aircraft changes and missing speed.
    fun update(speed: Double, timeMs: Long): Pair<Double, Double>? {
        samples.addLast(Sample(timeMs, speed))
        // Retain two samples for refresh intervals longer than the smoothing window.
        while (samples.size > 2 && timeMs - samples.first().timeMs > 1000) samples.removeFirst()
        if (samples.size < 2) return null
        val meanTime = samples.sumOf { (it.timeMs - timeMs) / 1000.0 } / samples.size
        var variance = 0.0
        var speedCovariance = 0.0
        var energyCovariance = 0.0
        for (sample in samples) {
            val centeredTime = (sample.timeMs - timeMs) / 1000.0 - meanTime
            val speedDifference = sample.speed - speed
            variance += centeredTime * centeredTime
            speedCovariance += centeredTime * speedDifference
            // Difference of v²/(2g), factored to avoid cancellation of nearly equal energies.
            energyCovariance += centeredTime * speedDifference * ((sample.speed + speed) / (2 * FlightCalculator.G))
        }
        if (variance <= 0.0) return null
        return speedCovariance / variance to energyCovariance / variance
    }
}
