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
import kotlin.test.*

class HudAlertRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun alertOnlyLayoutShowsConnectionLossAndSuppressesStaleAlerts() {
        val region = HudRegion("alerts", HudRegionContent.ALERTS, 0, 0, 400, 200)
        val scene = HudSceneLayout(400, 200, listOf(region))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene))
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        var alerts by mutableStateOf(emptyList<FlightAlert>())
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 200.dp)) {
            HudPanel(connection, settings, alerts, null) {}
        } } }
        compose.onNodeWithTag("hud-region-alerts").assertDoesNotExist()
        val oldAlert = FlightAlert.entries.first()
        for (state in listOf(ConnectionState.Connecting, ConnectionState.Delayed, ConnectionState.Disconnected("test"))) {
            compose.runOnIdle { connection = state; alerts = listOf(oldAlert) }
            compose.onNodeWithText(statusText(state)).assertIsDisplayed()
            compose.onNodeWithTag("flight-alert-${oldAlert.name}").assertDoesNotExist()
            compose.runOnIdle { alerts = emptyList() }
            compose.onNodeWithText(statusText(state)).assertIsDisplayed()
        }
        compose.runOnIdle { connection = hudPreviewFlight(); alerts = listOf(oldAlert) }
        compose.onNodeWithTag("flight-alert-${oldAlert.name}").assertIsDisplayed()
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithTag("hud-region-alerts").assertDoesNotExist()
        compose.runOnIdle {
            settings = settings.copy(hudSceneLayout = scene.copy(regions = listOf(region.copy(visible = false))))
            connection = ConnectionState.Delayed
        }
        compose.onNodeWithTag("hud-region-alerts").assertDoesNotExist()
    }

    @Test fun dedicatedRegionUsesItsHeightAndShrinksBackToScrollableAlerts() {
        val scene = HudSceneLayout(500, 500, listOf(HudRegion("alerts", HudRegionContent.ALERTS, 0, 0, 480, 100)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene))
        var alerts by mutableStateOf(FlightAlert.entries.take(10))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
            HudPanel(hudPreviewFlight(), settings, alerts, null) {}
        } } }
        val last = alerts.sortedBy { it.severity }.last()
        val row = compose.onNodeWithTag("flight-alert-${last.name}")
        compose.onNodeWithTag("flight-alert-scrollbar").assertExists()
        row.assertIsNotDisplayed()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene.resizeRegion("alerts", 480, 340)) }
        val bounds = compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top > 112.dp)
        alerts.forEach { compose.onNodeWithTag("flight-alert-${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("flight-alert-scrollbar").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene) }
        compose.onNodeWithTag("flight-alert-scrollbar").assertExists()
        row.performScrollTo().assertIsDisplayed()
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithTag("hud-region-alerts").assertDoesNotExist()
    }
}
