package voidmei.desktop

import androidx.compose.runtime.*

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import voidmei.telemetry.*

internal fun mapStates(endpoint: String): Flow<MapConnection> = flow {
    HttpTelemetryTransport(endpoint).use { transport -> emitAll(MapPoller(transport).states()) }
}.catch { error ->
    if (error is CancellationException) throw error
    emit(MapConnection.Unavailable(error.message ?: "地图不可用"))
}.flowOn(Dispatchers.IO)

internal fun Flow<MapConnection>.shareMap(scope: CoroutineScope): StateFlow<MapConnection> =
    stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), MapConnection.Connecting)

/** A transient telemetry delay does not end the current flight's shared map session. */
@Composable
internal fun rememberTelemetryMapSession(endpoint: String, connection: ConnectionState, sessionKey: Any?,
    source: (String) -> Flow<MapConnection> = ::mapStates): StateFlow<MapConnection> {
    var previous by remember(endpoint, sessionKey) { mutableStateOf(false to (null as String?)) }
    val flight = when (connection) {
        is ConnectionState.Flying -> true to connection.telemetry.aircraft
        ConnectionState.Delayed -> previous
        else -> false to null
    }
    SideEffect { previous = flight }
    return key(endpoint, sessionKey, flight) {
        val scope = rememberCoroutineScope()
        remember { source(endpoint).stateIn(scope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000, replayExpirationMillis = 0), MapConnection.Connecting) }
    }
}
