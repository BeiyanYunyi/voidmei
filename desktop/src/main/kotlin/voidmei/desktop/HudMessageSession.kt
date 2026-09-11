package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import voidmei.telemetry.*
import voidmei.config.AppSettings
import voidmei.config.HudRegionContent

internal fun AppSettings.needsHudMessages(): Boolean = hudEnabled && hudSceneLayout?.let { scene ->
    scene.enabled && scene.regions.any { it.visible && it.content == HudRegionContent.MESSAGES }
} == true

internal data class HudMessageSession(val state: HudMessageState, val reload: () -> Unit)

private fun hudMessageStates(endpoint: String, initial: HudMessageState) = flow {
    HttpTelemetryTransport(endpoint).use { transport -> emitAll(HudMessagePoller(transport).states(initial)) }
}

/** One application-level reader for the settings panel and all HUD message regions. */
@Composable
internal fun rememberHudMessageSession(endpoint: String, connection: ConnectionState, enabled: Boolean,
    sessionKey: Any?, source: (String, HudMessageState) -> Flow<HudMessageState> = ::hudMessageStates): HudMessageSession {
    var previous by remember(endpoint, sessionKey) { mutableStateOf(false to (null as String?)) }
    val flight = when (connection) {
        is ConnectionState.Flying -> true to connection.telemetry.aircraft
        ConnectionState.Delayed -> previous
        else -> false to null
    }
    SideEffect { previous = flight }
    var reload by remember { mutableStateOf(0) }
    var state by remember(endpoint, sessionKey, flight, reload) {
        mutableStateOf(HudMessageState(emptyList()))
    }
    LaunchedEffect(endpoint, sessionKey, flight, reload, enabled) {
        if (enabled && flight.first) {
            try {
                source(endpoint, state).collect { state = it }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { state = state.copy(error = e.message ?: "消息不可用") }
        }
    }
    return HudMessageSession(state) { reload++ }
}
