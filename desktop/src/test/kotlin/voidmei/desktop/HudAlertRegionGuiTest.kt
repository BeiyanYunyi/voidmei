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

    @Test fun warningColorFollowsHudSettingsInSceneAndVerticalLayouts() {
        var settings by mutableStateOf(AppSettings(hudWarningColor = "#00FF00",
            hudFields = emptyList(), hudAttitude = false, hudMechanization = false,
            hudEngineFields = emptyList(), hudSceneLayout = HudSceneLayout(500, 300,
                listOf(HudRegion("alerts", HudRegionContent.ALERTS, 0, 0, 500, 300)))))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 400.dp)) {
            HudPanel(hudPreviewFlight(), settings, listOf(FlightAlert.EMPTY_FUEL, FlightAlert.LOW_FUEL), null) {}
        } } }
        fun color(alert: FlightAlert): androidx.compose.ui.graphics.Color {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("flight-alert-${alert.name}").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style.color
        }
        assertEquals(androidx.compose.ui.graphics.Color.Green, color(FlightAlert.EMPTY_FUEL))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFD580), color(FlightAlert.LOW_FUEL))
        compose.runOnIdle { settings = settings.copy(hudWarningColor = "#0000FF", hudSceneLayout = null) }
        assertEquals(androidx.compose.ui.graphics.Color.Blue, color(FlightAlert.EMPTY_FUEL))
        compose.runOnIdle { settings = settings.copy(hudWarningColor = null) }
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF967B), color(FlightAlert.EMPTY_FUEL))
    }

    @Test fun previewIdentifiesAlertOverflowAndClearsItAfterResizingOrFiltering() {
        var region by mutableStateOf(HudRegion("alerts", HudRegionContent.ALERTS, 0, 0, 500, 100))
        var preview by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 600.dp)) {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(500, 600, listOf(region)))
            if (preview) HudLayoutPreview(settings, allAlerts = true)
            else HudPanel(hudPreviewFlight(), settings, FlightAlert.entries, null) {}
        } } }
        val badge = compose.onNodeWithTag("flight-alert-overflow")
        badge.assertIsDisplayed()
        compose.runOnIdle { region = region.copy(height = 600, fields = listOf("advisory")) }
        badge.assertDoesNotExist()
        compose.onNodeWithTag("flight-alert-HIGH_AOA").assertIsDisplayed()
        compose.runOnIdle { region = region.copy(height = 100, fields = null) }
        badge.assertIsDisplayed()
        compose.runOnIdle { preview = false }
        badge.assertDoesNotExist()
        compose.onNodeWithTag("flight-alert-scrollbar").assertIsDisplayed()
        compose.runOnIdle { preview = true; region = region.copy(fields = emptyList()) }
        badge.assertDoesNotExist()
        compose.onNodeWithTag("hud-region-alerts").assertDoesNotExist()
    }

    @Test fun longTitleAndLargeFontLeaveAlertsInsideRegionWithOneScrollArea() {
        val title = "发动机与飞行告警".repeat(6)
        val scene = HudSceneLayout(300, 220, listOf(HudRegion("alerts", HudRegionContent.ALERTS,
            0, 0, 300, 220, title = title)))
        val alerts = FlightAlert.entries.take(10)
        var fontScale by mutableStateOf(1.5f)
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp, 220.dp)) {
            HudPanel(hudPreviewFlight(), AppSettings(hudSceneLayout = scene, hudFontScale = fontScale), alerts, null) {}
        } } }
        for (scale in listOf(1.5f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            val region = compose.onNodeWithTag("hud-region-alerts").getUnclippedBoundsInRoot()
            val list = compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot()
            assertTrue(list.bottom <= region.bottom)
            assertTrue(list.bottom - list.top >= 60.dp)
            compose.onNodeWithTag("flight-alert-scrollbar").assertIsDisplayed()
            compose.onNodeWithTag("flight-alert-${alerts.sortedBy { it.severity }.last().name}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        }
    }

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
