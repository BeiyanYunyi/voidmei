package voidmei.telemetry

import voidmei.fm.*

/** Aircraft total; mixed propulsion kinds cannot share a dimensional denominator. */
fun Telemetry.powerPercent(parameters: FlightModelParameters): Double? {
    val peaks = parameters.enginePeaks
    val indices = parameters.engineBindings.map { it.telemetryIndex }
    if (indices.isEmpty() || indices.distinct().size != indices.size ||
        peaks.size != indices.size || peaks.map { it.telemetryIndex }.toSet() != indices.toSet() ||
        engines.size != indices.size || engines.map { it.index }.toSet() != indices.toSet() ||
        peaks.map { it.kind }.distinct().size != 1) return null
    var current = 0.0
    var maximum = 0.0
    for (peak in peaks) {
        if (!peak.peak.isFinite() || peak.peak <= 0) return null
        val engine = engines.single { it.index == peak.telemetryIndex }
        val value = when (peak.kind) {
            EnginePeakKind.SHAFT_POWER_HP -> engine.powerHp
            EnginePeakKind.THRUST_KGF -> engine.thrustKgf
        }?.takeIf { it.isFinite() && it >= 0 } ?: return null
        current += value
        maximum += peak.peak
    }
    if (!current.isFinite() || !maximum.isFinite()) return null
    return (current / maximum).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.times(100)
}


data class PowerPercentReading(val percent: Double, val source: String)

/** Per-engine FM reference only; the observed whole-aircraft peak cannot be reused here. */
fun ConnectionState.Flying.enginePowerPercentReading(index: Int, model: AircraftAlertModel?): PowerPercentReading? {
    val parameters = model?.parametersFor(telemetry.aircraft) ?: return null
    val engine = telemetry.engines.singleOrNull { it.index == index } ?: return null
    if (parameters.engineBindings.singleOrNull { it.telemetryIndex == index } == null) return null
    val peak = parameters.enginePeaks.singleOrNull { it.telemetryIndex == index }
        ?.takeIf { it.peak.isFinite() && it.peak > 0 } ?: return null
    val value = (if (peak.kind == EnginePeakKind.SHAFT_POWER_HP) engine.powerHp else engine.thrustKgf)
        ?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val ratio = (value / peak.peak).takeIf { it.isFinite() } ?: return null
    return PowerPercentReading(ratio.coerceIn(0.0, 1.0) * 100,
        if (peak.kind == EnginePeakKind.SHAFT_POWER_HP) "FM 功率峰值" else "FM 推力峰值")
}

fun ConnectionState.Flying.powerPercentReading(model: AircraftAlertModel?): PowerPercentReading? {
    val parameters = model?.parametersFor(telemetry.aircraft)
    parameters?.let { telemetry.powerPercent(it) }?.let { return PowerPercentReading(it, "FM 峰值") }
    val observed = metrics.observedEnginePeak ?: return null
    // A model with known bindings must agree with every observed engine and propulsion kind.
    if (parameters != null) {
        val bindings = parameters.engineBindings
        if (bindings.isEmpty() || bindings.size != telemetry.engines.size ||
            bindings.map { it.telemetryIndex }.toSet() != telemetry.engines.map { it.index }.toSet()) return null
        val compatible = bindings.all {
            if (observed.kind == EnginePeakKind.THRUST_KGF) it.type.equals("Jet", true)
            else it.type.lowercase() in setOf("inline", "radial", "turboprop")
        }
        if (!compatible) return null
    }
    return PowerPercentReading(observed.percent, "历史全油门峰值")
}
