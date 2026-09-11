package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*
import kotlin.test.*

class TelemetryMapSessionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun briefDelayKeepsSharedReaderButFlightAndEndpointChangesReplaceIt() {
        var connection by mutableStateOf<ConnectionState>(hudPreviewFlight())
        var endpoint by mutableStateOf("http://first")
        var generation by mutableStateOf(0)
        var shared: StateFlow<MapConnection>? = null
        var observed: Pair<MapConnection, MapConnection>? = null
        var starts = 0
        var active = 0
        compose.setContent {
            val session = rememberTelemetryMapSession(endpoint, connection, generation) {
                flow {
                    starts++; active++
                    try { emit(MapConnection.Waiting); awaitCancellation() } finally { active-- }
                }
            }
            SideEffect { shared = session }
            // Real HUD regions disappear during Delayed, temporarily removing their collectors.
            if (connection is ConnectionState.Flying) {
                val one by session.collectAsState()
                val two by session.collectAsState()
                SideEffect { observed = one to two }
            }
        }
        compose.waitUntil(5000) { starts == 1 && active == 1 && shared?.value == MapConnection.Waiting &&
            observed == (MapConnection.Waiting to MapConnection.Waiting) }
        val original = shared
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.runOnIdle { assertSame(original, shared); assertEquals(1, active); connection = hudPreviewFlight() }
        compose.runOnIdle { assertSame(original, shared); assertEquals(1, starts) }
        compose.runOnIdle { connection = ConnectionState.Disconnected("test") }
        compose.waitUntil(5000) { active == 0 }
        compose.runOnIdle { assertNotSame(original, shared); connection = hudPreviewFlight() }
        compose.waitUntil(5000) { starts == 2 && active == 1 }
        compose.runOnIdle { connection = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(aircraft = "other")) } }
        compose.waitUntil(5000) { starts == 3 && active == 1 }
        compose.runOnIdle { endpoint = "http://second" }
        compose.waitUntil(5000) { starts == 4 && active == 1 }
        compose.runOnIdle { generation++ }
        compose.waitUntil(5000) { starts == 5 && active == 1 }
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.waitUntil(8000) { active == 0 }
        compose.runOnIdle { assertEquals(MapConnection.Connecting, shared!!.value); connection = hudPreviewFlight().let {
            it.copy(telemetry = it.telemetry.copy(aircraft = "other"))
        } }
        compose.waitUntil(5000) { starts == 6 && active == 1 }
    }
}
