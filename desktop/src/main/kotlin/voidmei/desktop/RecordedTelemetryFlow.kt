package voidmei.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import voidmei.telemetry.ConnectionState

/** Process every state (including recording and alerts); only finished display snapshots may be skipped. */
internal fun <T> Flow<ConnectionState>.processBeforeDisplay(process: suspend (ConnectionState) -> T): Flow<T> =
    map { process(it) }.conflate().flowOn(Dispatchers.Default)

internal data class TelemetryDisplay(
    val connection: ConnectionState,
    val thermal: voidmei.telemetry.EngineThermalObservation?,
    val alerts: List<voidmei.telemetry.FlightAlert>,
)
