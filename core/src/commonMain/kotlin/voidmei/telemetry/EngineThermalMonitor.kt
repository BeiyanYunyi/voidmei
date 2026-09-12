package voidmei.telemetry

import voidmei.fm.EngineThermalParameters

enum class EngineTemperatureChannel { WATER, OIL }
data class EngineThermalWarning(val telemetryIndex: Int, val channel: EngineTemperatureChannel)

/** A sampled estimate must not be displayed with another aircraft, model or telemetry frame. */
data class EngineThermalObservation(
    val telemetry: Telemetry,
    val parameters: List<EngineThermalParameters>,
    val budgets: List<EngineThermalBudget>,
) {
    /** Even the upper budget estimate is below the legacy five-minute warning threshold. */
    fun warningEngines(state: ConnectionState, model: AircraftAlertModel?): Set<Int> =
        warningChannels(state, model).map { it.telemetryIndex }.toSet()

    fun warningChannels(state: ConnectionState, model: AircraftAlertModel?): Set<EngineThermalWarning> = buildSet {
        budgetsFor(state, model).forEach { engine ->
            listOf(EngineTemperatureChannel.WATER to engine.water, EngineTemperatureChannel.OIL to engine.oil).forEach { (channel, budget) ->
                val range = budget?.activeRemaining
                if (range != null && range.minimumSeconds.isFinite() && range.maximumSeconds.isFinite() &&
                    range.minimumSeconds >= 0 && range.maximumSeconds >= range.minimumSeconds && range.maximumSeconds < 300)
                    add(EngineThermalWarning(engine.telemetryIndex, channel))
            }
        }
    }

    fun budgetsFor(state: ConnectionState, model: AircraftAlertModel?): List<EngineThermalBudget> =
        if (state is ConnectionState.Flying && state.telemetry == telemetry &&
            model?.parametersFor(telemetry.aircraft)?.engineThermals == parameters) budgets else emptyList()
}

class EngineThermalMonitor {
    private val tracker = EngineThermalTracker()

    fun update(state: ConnectionState, model: AircraftAlertModel?, nowMs: Long): EngineThermalObservation? {
        if (state == ConnectionState.Delayed) {
            tracker.pause()
            return null
        }
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
