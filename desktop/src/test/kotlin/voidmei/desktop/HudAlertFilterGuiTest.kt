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
import kotlin.test.assertEquals
import voidmei.config.*
import voidmei.telemetry.*

class HudAlertFilterGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersHideOnlyUnmatchedRegionsAndKeepConnectionStatus() {
        val one = HudRegion("one", HudRegionContent.ALERTS, 0, 0, 350, 220, fields = listOf("warning", "future"))
        var scene by mutableStateOf(HudSceneLayout(700, 220, listOf(one,
            one.copy(id = "two", x = 350, fields = listOf("advisory")))))
        val original = hudPreviewFlight()
        var connection by mutableStateOf<ConnectionState>(original)
        var alerts by mutableStateOf(listOf(FlightAlert.EMPTY_FUEL, FlightAlert.LOW_FUEL))
        compose.setContent { MaterialTheme { Column {
            HudRegionFieldsSettings(scene.regions.first(), AppSettings()) { region ->
                scene = scene.copy(regions = scene.regions.map { if (it.id == region.id) region else it })
            }
            Box(Modifier.size(700.dp, 220.dp)) {
                HudPanel(connection, AppSettings(hudSceneLayout = scene), alerts, null) {}
            }
        } } }
        compose.onNode(hasTestTag("flight-alert-EMPTY_FUEL") and hasAnyAncestor(hasTestTag("hud-region-one"))).assertIsDisplayed()
        compose.onNode(hasTestTag("flight-alert-LOW_FUEL") and hasAnyAncestor(hasTestTag("hud-region-two"))).assertIsDisplayed()
        compose.onNodeWithTag("hud-region-alert-one-warning").performClick()
        compose.onNodeWithTag("hud-region-one").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-two").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.onNodeWithTag("hud-region-one").assertIsDisplayed()
        compose.onAllNodesWithTag("flight-alerts").assertCountEquals(0)
        compose.runOnIdle { connection = ConnectionState.Disconnected("offline") }
        compose.onAllNodesWithText("连接中断 · 自动重试 · offline").assertCountEquals(2)
        compose.runOnIdle { connection = original }
        compose.onNodeWithTag("hud-region-one").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-alert-one-advisory").performClick()
        compose.onAllNodesWithTag("flight-alert-LOW_FUEL").assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(listOf("future", "advisory"), scene.regions.first().fields)
            assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
            alerts = listOf(FlightAlert.EMPTY_FUEL)
        }
        compose.onAllNodesWithTag("flight-alerts").assertCountEquals(0)
        compose.onNodeWithTag("hud-region-one").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-two").assertDoesNotExist()
        compose.runOnIdle { scene = scene.copy(regions = scene.regions.map { it.copy(fields = null) }) }
        compose.onAllNodesWithTag("flight-alert-EMPTY_FUEL").assertCountEquals(2)
    }
}
