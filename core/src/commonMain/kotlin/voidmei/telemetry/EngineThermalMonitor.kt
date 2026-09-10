package voidmei.telemetry

import voidmei.fm.EngineThermalParameters

/** A sampled estimate must not be displayed with another aircraft, model or telemetry frame. */
data class EngineThermalObservation(
    val telemetry: Telemetry,
    val parameters: List<EngineThermalParameters>,
    val budgets: List<EngineThermalBudget>,
) {
    /** Even the upper budget estimate is below the legacy five-minute warning threshold. */
    fun warningEngines(state: ConnectionState, model: AircraftAlertModel?): Set<Int> =
        budgetsFor(state, model).filter { engine ->
            listOfNotNull(engine.water?.activeRemaining, engine.oil?.activeRemaining).any {
                it.minimumSeconds.isFinite() && it.maximumSeconds.isFinite() &&
                    it.minimumSeconds >= 0 && it.maximumSeconds >= it.minimumSeconds && it.maximumSeconds < 300
            }
        }.map { it.telemetryIndex }.toSet()

    fun budgetsFor(state: ConnectionState, model: AircraftAlertModel?): List<EngineThermalBudget> =
        if (state is ConnectionState.Flying && state.telemetry == telemetry &&
            model?.parametersFor(telemetry.aircraft)?.engineThermals == parameters) budgets else emptyList()
}

class EngineThermalMonitor {
    private val tracker = EngineThermalTracker()

    fun update(state: ConnectionState, model: AircraftAlertModel?, nowMs: Long): EngineThermalObservation? {
        val telemetry = (state as? ConnectionState.Flying)?.telemetry
        val aircraft = telemetry?.aircraft?.takeIf { it.isNotBlank() }
        val parameters = model?.parametersFor(aircraft)?.engineThermals.orEmpty()
        if (telemetry == null || aircraft == null || parameters.isEmpty()) {
            tracker.reset()
            return null
        }
        return EngineThermalObservation(telemetry, parameters,
            tracker.update(aircraft, parameters, telemetry.engines, nowMs))
    }
}
