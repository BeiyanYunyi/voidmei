package voidmei.telemetry

import kotlin.math.abs

enum class CockpitAltitudeUnit { METRES, FEET }

/** Infers an instrument scale from a continuous, moving ten-second altitude window. */
class CockpitUnitEstimator {
    private data class Sample(val aircraft: String, val time: Long, val metres: Double, val instrument: Double)
    private var origin: Sample? = null
    private var previous: Sample? = null
    private var confirmed: CockpitAltitudeUnit? = null

    fun reset() { origin = null; previous = null; confirmed = null }

    fun update(telemetry: Telemetry, timeMs: Long): CockpitAltitudeUnit? {
        val aircraft = telemetry.aircraft
        val metres = telemetry.altitudeM
        val instrument = telemetry.altimeterRaw
        if (aircraft == null || metres == null || !metres.isFinite() || instrument == null || !instrument.isFinite()) {
            reset()
            return null
        }
        val sample = Sample(aircraft, timeMs, metres, instrument)
        val last = previous
        if (last == null || last.aircraft != aircraft || timeMs <= last.time || timeMs - last.time !in 1..2000) reset()
        previous = sample
        val start = origin
        if (start == null) { origin = sample; return confirmed }
        val duration = timeMs - start.time
        if (duration < 10000) return confirmed
        val altitudeChange = metres - start.metres
        if (abs(altitudeChange) >= 10 && altitudeChange.isFinite()) {
            val ratio = (instrument - start.instrument) / altitudeChange
            confirmed = when {
                ratio in 0.85..1.15 -> CockpitAltitudeUnit.METRES
                ratio in 2.95..3.61 -> CockpitAltitudeUnit.FEET
                else -> null
            }
            origin = sample
        } else if (duration >= 30000) origin = sample
        return confirmed
    }
}
