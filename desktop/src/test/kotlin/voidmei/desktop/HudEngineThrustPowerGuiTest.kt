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

    @Test fun independentEnginesClearMissingThrustAndShareCurrentTas() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"thrust 1, kgs":100,"thrust 2, kgs":200}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(telemetry, FlightMetrics()))
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 200, fields = listOf("thrust_power"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 200,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))),
            hudFields = emptyList(), hudAttitude = false, hudMechanization = false,
            hudEngineIndex = 2, hudEngineFields = listOf("thrust_power")))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 400.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("98.1 kW").assertIsDisplayed()
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry.copy(engines = telemetry.engines.map {
            if (it.index == 1) it.copy(thrustKgf = null) else it
        })) }
        compose.onNodeWithText("98.1 kW").assertDoesNotExist()
        compose.onNodeWithText("— kW").assertIsDisplayed()
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry.copy(tasKmh = null)) }
        compose.onAllNodesWithText("— kW").assertCountEquals(2)
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry); settings = settings.copy(hudSceneLayout = null) }
        compose.onNodeWithText("196.1 kW").assertIsDisplayed()
    }
}
