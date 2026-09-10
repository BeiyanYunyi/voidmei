package voidmei.telemetry

import voidmei.fm.EnginePeakKind

data class ObservedEnginePeak(val kind: EnginePeakKind, val percent: Double, val reference: Double? = null)

/** Whole-aircraft full-throttle reference, never a claim about theoretical engine limits. */
class ObservedEnginePeakTracker {
    private var aircraft: String? = null
    private var indices = emptySet<Int>()
    private var kind: EnginePeakKind? = null
    private var previous: Long? = null
    private var fullSince: Long? = null
    private var candidate = 0.0
    private var peak: Double? = null

    fun reset() {
        aircraft = null; indices = emptySet(); kind = null; previous = null
        fullSince = null; candidate = 0.0; peak = null
    }

    fun update(t: Telemetry, timeMs: Long): ObservedEnginePeak? {
        val engines = t.engines
        val currentKind = when {
            engines.isEmpty() -> null
            engines.all { (it.magneto?.let { m -> m.isFinite() && m >= 0 } == true) ||
                (it.propellerPitchDeg?.isFinite() == true) || (it.powerHp?.let { p -> p.isFinite() && p > 0 } == true) } -> EnginePeakKind.SHAFT_POWER_HP
            // Explicit zero shaft power is evidence; absent channels alone do not identify a jet.
            engines.all { it.powerHp == 0.0 && it.propellerPitchDeg == null &&
                (it.magneto == null || (it.magneto.isFinite() && it.magneto < 0)) } -> EnginePeakKind.THRUST_KGF
            else -> null
        }
        val ids = engines.map { it.index }.toSet()
        val values = engines.map { if (currentKind == EnginePeakKind.SHAFT_POWER_HP) it.powerHp else it.thrustKgf }
        if (t.aircraft == null || currentKind == null || ids.size != engines.size ||
            values.any { it == null || !it.isFinite() || it < 0 } ||
            engines.any { it.throttlePercent?.let { v -> v.isFinite() && v >= 0 } != true }) {
            reset(); return null
        }
        val current = values.sumOf { it!! }
        if (!current.isFinite()) { reset(); return null }
        val last = previous
        if (aircraft != t.aircraft || indices != ids || kind != currentKind || last == null ||
            timeMs <= last || timeMs - last !in 1..2000) reset()
        aircraft = t.aircraft; indices = ids; kind = currentKind; previous = timeMs
        if (engines.all { it.throttlePercent!! >= 100 }) {
            val start = fullSince ?: timeMs.also { fullSince = it }
            candidate = maxOf(candidate, current)
            if (timeMs - start >= 5000 && candidate > 0) peak = maxOf(peak ?: 0.0, candidate)
        } else { fullSince = null; candidate = 0.0 }
        return peak?.let { ObservedEnginePeak(currentKind, (current / it).coerceIn(0.0, 1.0) * 100, it) }
    }
}
