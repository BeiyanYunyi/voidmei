package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*

class HudEngineThrustPowerGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingInputReasonsFollowSelectionAndRecoverAtZeroSpeed() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"thrust 1, kgs":100,"power 1, hp":200}""",
            """{"valid":true,"type":"test"}""")!!
        var engine by mutableStateOf(telemetry.engines.single().copy(powerHp = 0.0))
        var speed by mutableStateOf<Double?>(360.0)
        var fields by mutableStateOf(listOf(HudEngineField.THRUST_POWER, HudEngineField.PROPULSIVE_EFFICIENCY))
        compose.setContent { MaterialTheme { HudEnginePanel(listOf(engine), 1, fields = fields, tasKmh = speed) } }
        compose.onNodeWithText("98.1 kW").assertIsDisplayed()
        compose.onNodeWithText("推进效率估计：缺少本发动机正轴功率").assertIsDisplayed()
        compose.runOnIdle { speed = null; engine = engine.copy(thrustKgf = null) }
        compose.onNodeWithText("推进功率：缺少有效 TAS、本发动机有效推力").assertIsDisplayed()
        compose.onNodeWithText("推进效率估计：缺少有效 TAS、本发动机有效推力、本发动机正轴功率").assertIsDisplayed()
        compose.runOnIdle { fields = listOf(HudEngineField.RPM) }
        compose.onAllNodes(hasText("缺少", substring = true)).assertCountEquals(0)
        compose.runOnIdle {
            fields = listOf(HudEngineField.THRUST_POWER, HudEngineField.PROPULSIVE_EFFICIENCY)
            speed = 0.0; engine = telemetry.engines.single()
        }
        compose.onAllNodes(hasText("缺少", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("0.0 kW").assertIsDisplayed()
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
    }

    @Test fun reconnectAndAircraftChangeCannotReuseOldPropulsionReadings() {
        val original = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"thrust 1, kgs":100,"power 1, hp":200}""",
            """{"valid":true,"type":"first"}""")!!
        var connection by mutableStateOf<ConnectionState>(ConnectionState.Flying(original, FlightMetrics()))
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 200,
            fields = listOf("thrust_power", "propulsive_efficiency"))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 200,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudPanel(connection, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("98.1 kW").assertIsDisplayed()
        compose.onNodeWithText("66.7 %").assertIsDisplayed()
        for (state in listOf(ConnectionState.Delayed, ConnectionState.Disconnected("test"),
                ConnectionState.Connecting, ConnectionState.WaitingForFlight)) {
            compose.runOnIdle { connection = state }
            compose.onNodeWithText("98.1 kW").assertDoesNotExist()
            compose.onNodeWithText("66.7 %").assertDoesNotExist()
            compose.onAllNodesWithText(statusText(state)).assertCountEquals(2)
            compose.runOnIdle { connection = ConnectionState.Flying(original, FlightMetrics()) }
            compose.onNodeWithText("98.1 kW").assertIsDisplayed()
        }
        val replacement = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"thrust 2, kgs":300,"power 2, hp":1000}""",
            """{"valid":true,"type":"second"}""")!!
        compose.runOnIdle { connection = ConnectionState.Flying(replacement, FlightMetrics()) }
        compose.onNodeWithText("98.1 kW").assertDoesNotExist()
        compose.onNodeWithText("66.7 %").assertDoesNotExist()
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
        compose.onNodeWithText("294.2 kW").assertIsDisplayed()
        compose.onNodeWithText("40.0 %").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.Flying(replacement.copy(
            engines = replacement.engines + replacement.engines), FlightMetrics()) }
        compose.onNodeWithText("294.2 kW").assertDoesNotExist()
        compose.onNodeWithText("40.0 %").assertDoesNotExist()
        compose.onAllNodesWithText("此编号无可用发动机数据").assertCountEquals(2)
    }

    @Test fun independentEnginesClearMissingThrustAndShareCurrentTas() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"thrust 1, kgs":100,"thrust 2, kgs":200,"power 1, hp":200,"power 2, hp":800}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(telemetry, FlightMetrics()))
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 200, fields = listOf("thrust_power", "propulsive_efficiency"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 200,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))),
            hudFields = emptyList(), hudAttitude = false, hudMechanization = false,
            hudEngineIndex = 2, hudEngineFields = listOf("thrust_power", "propulsive_efficiency")))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 400.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("98.1 kW").assertIsDisplayed()
        compose.onNodeWithText("66.7 %").assertIsDisplayed()
        compose.onNodeWithText("33.4 %").assertIsDisplayed()
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry.copy(engines = telemetry.engines.map {
            if (it.index == 1) it.copy(thrustKgf = null) else it
        })) }
        compose.onNodeWithText("98.1 kW").assertDoesNotExist()
        compose.onNodeWithText("66.7 %").assertDoesNotExist()
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithText("33.4 %").assertIsDisplayed()
        compose.onNodeWithText("— kW").assertIsDisplayed()
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
        compose.onAllNodesWithText("推进功率：缺少本发动机有效推力").assertCountEquals(1)
        compose.onAllNodesWithText("推进效率估计：缺少本发动机有效推力").assertCountEquals(1)
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry.copy(tasKmh = null)) }
        compose.onAllNodesWithText("— kW").assertCountEquals(2)
        compose.onAllNodesWithText("— %").assertCountEquals(2)
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry); settings = settings.copy(hudSceneLayout = null) }
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
        compose.onNodeWithText("33.4 %").assertIsDisplayed()
    }
}
