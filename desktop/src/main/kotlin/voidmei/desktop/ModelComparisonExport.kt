package voidmei.desktop

import voidmei.fm.ModelComparison

internal fun modelComparisonCsv(baseline: NamedModel, current: NamedModel,
    sweep: Double, flaps: Double, fuelFraction: Double): String {
    fun row(values: List<Any?>) = values.joinToString(",") { "\"${it?.toString().orEmpty().replace("\"", "\"\"")}\"" } + "\n"
    return buildString {
        append(row(listOf("metric", "unit", "baseline_aircraft", "current_aircraft", "baseline", "current", "difference",
            "sweep_ratio", "flaps_percent", "fuel_fraction", "baseline_source", "current_source",
            "baseline_snapshot_utc", "current_snapshot_utc")))
        ModelComparison.compare(baseline.parameters, current.parameters, sweep, flaps, fuelFraction).forEach { value ->
            append(row(listOf(value.label, value.unit, baseline.aircraft, current.aircraft, value.baseline, value.current,
                value.delta, sweep, flaps, fuelFraction, baseline.source, current.source, baseline.capturedAt, current.capturedAt)))
        }
    }
}
