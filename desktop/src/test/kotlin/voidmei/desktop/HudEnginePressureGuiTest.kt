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

class HudEnginePressureGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionsShowSelectedEnginePressureUnitsWithoutBorrowingFromAnotherEngine() {
        val telemetry = TelemetryParser.parse(
            """{"valid":true,"manifold pressure 1, atm":1.5,"manifold pressure 2, atm":0.5}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(telemetry, FlightMetrics()))
        val scene = HudSceneLayout(800, 300, listOf(
            HudRegion("one", HudRegionContent.ENGINE, 0, 0, 380, 280, engineIndex = 1, fields = listOf("manifold")),
            HudRegion("two", HudRegionContent.ENGINE, 400, 0, 380, 280, engineIndex = 2, fields = listOf("manifold_inhg", "boost_psi"))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudPanel(flight, AppSettings(hudSceneLayout = scene), emptyList(), null) {}
        } } }
        compose.onNodeWithText("1.50 atm").assertIsDisplayed()
        compose.onNodeWithText("15.0 inHg").assertIsDisplayed()
        compose.onNodeWithText("-7.3 psi").assertIsDisplayed()
        compose.onNodeWithText("增压（相对1atm）").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = telemetry.copy(engines = telemetry.engines.filter { it.index == 1 })) }
        compose.onNodeWithText("1.50 atm").assertIsDisplayed()
        compose.onNodeWithText("15.0 inHg").assertDoesNotExist()
        compose.onNodeWithText("-7.3 psi").assertDoesNotExist()
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
    }
}
