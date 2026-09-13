package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import voidmei.config.AppSettings
import kotlin.test.assertEquals
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.TelemetryParser

class AttitudeRefreshGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsControlsChangeOnlyDisplayCadence() {
        var settings by mutableStateOf(AppSettings(pollIntervalMs = 25))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 700.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("instruments")
        compose.onNodeWithTag("attitude-refresh-enabled").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(40, settings.hudAttitudeRefreshMs); assertEquals(25L, settings.pollIntervalMs) }
        compose.onNodeWithTag("attitude-refresh-interval").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.runOnIdle { assertEquals(75, settings.hudAttitudeRefreshMs) }
        compose.onNodeWithTag("attitude-refresh-enabled").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, settings.hudAttitudeRefreshMs); assertEquals(25L, settings.pollIntervalMs) }
        compose.onNodeWithTag("attitude-refresh-interval").assertDoesNotExist()
    }
    @Test fun retainsLatestSampleAndResetsOnAircraftOrIntervalChange() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!.copy(aircraft = "A", headingDeg = 0.0))
        var interval by mutableStateOf(100)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { AttitudePanel(telemetry, refreshMs = interval) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("姿态 · 航向 000°").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(headingDeg = 20.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { telemetry = telemetry.copy(headingDeg = 40.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("姿态 · 航向 000°").assertExists()
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithText("姿态 · 航向 040°").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "B", headingDeg = 90.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("姿态 · 航向 090°").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(headingDeg = 180.0); interval = 0 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("姿态 · 航向 180°").assertExists()
    }
}
