package voidmei.fm

/** Shared tank capacity; consumption in kg/s for each telemetry engine while throttle exceeds 100%. */
data class WepFuelModel(val capacityKg: Double, val consumptionKgPerSecond: Map<Int, Double>) {
    /** Full shared tank, all engines continuously consuming at their model rates. */
    fun fullConsumptionDurationSeconds(): Double? {
        if (!capacityKg.isFinite() || capacityKg <= 0 || consumptionKgPerSecond.isEmpty() ||
            consumptionKgPerSecond.any { (index, rate) -> index <= 0 || !rate.isFinite() || rate < 0 }) return null
        val total = consumptionKgPerSecond.values.sum()
        if (!total.isFinite() || total <= 0) return null
        return (capacityKg / total).takeIf { it.isFinite() && it > 0 }
    }
}

object WepFuelExtractor {
    fun extract(document: BlkBlock): WepFuelModel? {
        val fields = document.fields()
        val capacityField = fields.filter { it.first.equals("Mass.MaxNitro", true) || it.first.equals("MaxNitro", true) }
            .singleOrNull()?.second ?: return null
        fun BlkField.nonnegative(): Double? = takeIf { it.type.lowercase() in setOf("r", "i", "i64") }
            ?.number()?.takeIf { it.isFinite() && it >= 0 }
        val capacity = capacityField.nonnegative()?.takeIf { it > 0 } ?: return null
        val instances = EngineInstanceResolver.resolve(document)
        if (instances.engines.isEmpty() || instances.issues.isNotEmpty()) return null
        val rates = mutableMapOf<Int, Double>()
        for (engine in instances.engines) {
            val rate = engine.parameters.fields().filter { it.first.equals("Afterburner.NitroConsumption", true) }
                .singleOrNull()?.second?.nonnegative() ?: return null
            if (rates.put(engine.binding.telemetryIndex, rate) != null) return null
        }
        if (rates.values.none { it > 0 }) return null
        return WepFuelModel(capacity, rates)
    }
}
