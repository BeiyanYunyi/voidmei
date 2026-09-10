package voidmei.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow

sealed interface MapConnection {
    data object Connecting : MapConnection
    data object Waiting : MapConnection
    data class Available(val snapshot: MapSnapshot) : MapConnection
    data class Unavailable(val reason: String) : MapConnection
}

class MapPoller(private val transport: TelemetryTransport, private val intervalMs: Long = 1000) {
    init { require(intervalMs in 100..10000) }
    fun states() = flow {
        emit(MapConnection.Connecting)
        while (currentCoroutineContext().isActive) {
            val next = supervisorScope {
                val request = async { read() }
                val timely = withTimeoutOrNull(1000) { request.await() }
                if (timely == null) emit(MapConnection.Unavailable("地图更新延迟"))
                timely ?: request.await()
            }
            emit(next)
            delay(intervalMs)
        }
    }

    private suspend fun read(): MapConnection = try {
        withTimeout(2500) {
            val before = MapTelemetryParser.info(transport.get("/map_info.json"))
            if (before == null) MapConnection.Waiting else {
                val body = transport.get("/map_obj.json")
                val after = MapTelemetryParser.info(transport.get("/map_info.json"))
                // Bracket object retrieval with metadata reads; reject a map change.
                if (after != before) MapConnection.Waiting else
                    MapConnection.Available(MapSnapshot(before, MapTelemetryParser.objects(body)))
            }
        }
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        MapConnection.Unavailable("地图请求超时")
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { MapConnection.Unavailable(e.message ?: "无法读取地图") }
}
