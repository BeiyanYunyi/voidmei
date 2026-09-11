package voidmei.telemetry

/** Minimum active budget across the selected engine's modelled channels, preserving uncertainty. */
fun EngineThermalObservation.hudBudget(state: ConnectionState, model: AircraftAlertModel?, engineIndex: Int = 1): ThermalBudgetRange? {
    val budget = budgetsFor(state, model).singleOrNull { it.telemetryIndex == engineIndex } ?: return null
    val thermal = parameters.singleOrNull { it.telemetryIndex == engineIndex } ?: return null
    val channels = mutableListOf<ThermalChannelBudget>()
    if (thermal.bands.any { it.waterTemperatureC != null && (it.workSeconds ?: 0.0) > 0 }) {
        channels += budget.water ?: return null
    }
    if (thermal.bands.any { it.oilTemperatureC != null && (it.workSeconds ?: 0.0) > 0 }) {
        channels += budget.oil ?: return null
    }
    val active = channels.mapNotNull { it.activeRemaining }
    if (active.isEmpty() || active.any { !it.minimumSeconds.isFinite() || !it.maximumSeconds.isFinite() ||
            it.minimumSeconds < 0 || it.maximumSeconds < it.minimumSeconds }) return null
    return ThermalBudgetRange(active.minOf { it.minimumSeconds }, active.minOf { it.maximumSeconds })
}

/** Round the interval outwards, so display precision never narrows its uncertainty. */
fun ThermalBudgetRange.roundForDisplay(): ThermalBudgetRange? {
    if (!minimumSeconds.isFinite() || !maximumSeconds.isFinite() || minimumSeconds < 0 || maximumSeconds < minimumSeconds) return null
    val lower = if (minimumSeconds > Double.MAX_VALUE / 10) minimumSeconds else kotlin.math.floor(minimumSeconds * 10) / 10
    val upper = roundUpperBound(maximumSeconds, 1) ?: return null
    return ThermalBudgetRange(minOf(lower, minimumSeconds), maxOf(upper, maximumSeconds))
}
