package voidmei.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

fun interface TelemetryTransport {
    suspend fun get(path: String): String
}

class TelemetryPoller(
    private val transport: TelemetryTransport,
    private val intervalMs: Long = 100,
    private val intervalProvider: (() -> Long)? = null,
) {
    init { require(intervalMs in 20..5000) }

    /** Cold flow: cancelling its collector cancels requests and polling together. */
    fun states(): Flow<ConnectionState> = flow {
        val calculator = FlightCalculator()
        val origin = TimeSource.Monotonic.markNow()
        emit(ConnectionState.Connecting)
        while (currentCoroutineContext().isActive) {
            val next = try {
                val telemetry = withTimeout(2500) {
                    coroutineScope {
                        val state = async { transport.get("/state") }
                        val indicators = async { transport.get("/indicators") }
                        TelemetryParser.parse(state.await(), indicators.await())
                    }
                }
                if (telemetry == null) {
                    calculator.reset()
                    ConnectionState.WaitingForFlight
                } else ConnectionState.Flying(telemetry, calculator.update(telemetry, origin.elapsedNow().inWholeMilliseconds))
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                calculator.reset()
                ConnectionState.Disconnected("Telemetry request timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                calculator.reset()
                ConnectionState.Disconnected(e.message ?: "Telemetry unavailable")
            }
            emit(next)
            // Read settings between samples without recreating the transport or calculator.
            val nextInterval = intervalProvider?.invoke() ?: intervalMs
            require(nextInterval in 20..5000) { "Telemetry interval must be between 20 and 5000 ms" }
            delay(nextInterval)
        }
    }
}
