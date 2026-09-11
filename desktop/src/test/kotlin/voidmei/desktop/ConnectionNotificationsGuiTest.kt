package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.ConnectionState
import voidmei.telemetry.TelemetryParser
import voidmei.telemetry.FlightMetrics
import kotlin.test.*

class ConnectionNotificationsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun flightSamplesAndAircraftChangesDoNotRepeatTheConnectionNotice() {
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"first"}""")!!
        var state by mutableStateOf<ConnectionState>(ConnectionState.Connecting)
        val messages = mutableListOf<String>()
        compose.setContent { ConnectionNotificationEffect(state, "http://127.0.0.1:8111", 0) { title, _ -> messages += title } }
        compose.runOnIdle { state = ConnectionState.Flying(telemetry, FlightMetrics()) }
        compose.runOnIdle { assertEquals(listOf("已收到飞行数据"), messages) }
        repeat(10) { index ->
            compose.runOnIdle { state = ConnectionState.Flying(telemetry.copy(aircraft = "plane-$index"), FlightMetrics()) }
        }
        compose.runOnIdle { assertEquals(1, messages.size); state = ConnectionState.WaitingForFlight }
        compose.runOnIdle { assertEquals(2, messages.size); state = ConnectionState.Flying(telemetry, FlightMetrics()) }
        compose.runOnIdle { assertEquals(3, messages.size) }
    }

    @Test fun reportsTransitionsWithoutReplayingDisabledOrUnavailableEvents() {
        var state by mutableStateOf<ConnectionState>(ConnectionState.Connecting)
        var enabled by mutableStateOf(true)
        var available by mutableStateOf(true)
        var endpoint by mutableStateOf("http://127.0.0.1:8111")
        var generation by mutableStateOf(0)
        val messages = mutableListOf<Pair<String, String>>()
        compose.setContent {
            ConnectionNotificationEffect(state, endpoint, generation) { title, message ->
                if (enabled && available) messages += title to message
            }
        }
        compose.runOnIdle { assertTrue(messages.isEmpty()); state = ConnectionState.WaitingForFlight }
        compose.runOnIdle { assertEquals(1, messages.size); state = ConnectionState.Disconnected("first failure") }
        compose.runOnIdle { assertEquals(2, messages.size); state = ConnectionState.Disconnected("different failure") }
        compose.runOnIdle { assertEquals(2, messages.size); enabled = false; state = ConnectionState.WaitingForFlight }
        compose.runOnIdle { enabled = true }
        compose.runOnIdle { assertEquals(2, messages.size); available = false; state = ConnectionState.Disconnected("offline") }
        compose.runOnIdle { available = true }
        compose.runOnIdle { assertEquals(2, messages.size); state = ConnectionState.WaitingForFlight }
        compose.runOnIdle { assertEquals(3, messages.size); endpoint = "http://127.0.0.1:9222"; generation++ }
        compose.runOnIdle { assertEquals(3, messages.size); state = ConnectionState.Connecting }
        compose.runOnIdle { state = ConnectionState.WaitingForFlight }
        compose.runOnIdle { assertEquals(4, messages.size); assertTrue(messages.last().second.endsWith(":9222")) }
    }
}
