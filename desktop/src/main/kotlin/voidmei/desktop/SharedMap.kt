package voidmei.desktop

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
