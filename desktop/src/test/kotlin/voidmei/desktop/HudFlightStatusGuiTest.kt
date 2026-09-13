package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

class HudFlightStatusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hidingOneFlightHeaderKeepsReadingsAndAllConnectionWarnings() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 300, 200, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(600, 200,
            listOf(region, region.copy(id = "two", x = 300)))))
        val flight = hudPreviewFlight()
        var connection by mutableStateOf<ConnectionState>(flight)
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(450.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(600.dp, 200.dp)) { HudPanel(connection, settings, emptyList(), null) {} }
        } } }
        compose.onAllNodesWithText(statusText(flight)).assertCountEquals(2)
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-flight-status-one").performScrollTo().performClick()
        compose.onAllNodesWithText(statusText(flight)).assertCountEquals(1)
        compose.onAllNodesWithText("340 km/h").assertCountEquals(2)
        for (state in listOf(ConnectionState.Delayed, ConnectionState.Disconnected("test"), ConnectionState.WaitingForFlight)) {
            compose.runOnIdle { connection = state }
            compose.onAllNodesWithText(statusText(state)).assertCountEquals(2)
        }
        compose.runOnIdle { connection = flight }
        compose.onAllNodesWithText(statusText(flight)).assertCountEquals(1)
        compose.onNodeWithTag("hud-region-flight-status-one").performClick()
        compose.onAllNodesWithText(statusText(flight)).assertCountEquals(2)
    }
}
