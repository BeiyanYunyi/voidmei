package voidmei.telemetry

import voidmei.fm.WepFuelModel

data class WepFuelObservation(val telemetry: Telemetry, val parameters: WepFuelModel, val estimate: WepFuelEstimate) {
    fun estimateFor(flight: ConnectionState.Flying, model: AircraftAlertModel?): WepFuelEstimate? =
        estimate.takeIf { flight.telemetry == telemetry && model?.parametersFor(telemetry.aircraft)?.wepFuel == parameters }
}

class WepFuelMonitor {
    private val tracker = WepFuelTracker()
    fun update(state: ConnectionState, model: AircraftAlertModel?, timeMs: Long): ConnectionState {
        if (state == ConnectionState.Delayed) {
            tracker.pause()
            return state
        }
        val flight = state as? ConnectionState.Flying
        val t = flight?.telemetry
        val parameters = model?.parametersFor(t?.aircraft)?.wepFuel
        val estimate = tracker.update(t, parameters, timeMs)
        return flight?.copy(metrics = flight.metrics.copy(wepFuel =
            if (estimate != null && parameters != null) WepFuelObservation(flight.telemetry, parameters, estimate) else null)) ?: state
    }
}
