package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.test.assertEquals
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*

class RadioAltitudeGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsControlsSelectEachModeAndRestoreDefault() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.onNodeWithText("HUD 字段设置").performClick()
        for (mode in listOf(HudAltitudeMode.LOW_RADAR, HudAltitudeMode.ALWAYS_RADAR, HudAltitudeMode.SEA_LEVEL)) {
            compose.onNodeWithTag("hud-altitude-${mode.id}").performScrollTo().performClick().assertIsSelected()
            assertEquals(mode, settings.hudAltitudeMode)
        }
        compose.onNodeWithTag("hud-altitude-always_radar").performClick()
        compose.onNodeWithText("恢复默认").performScrollTo().performClick()
        assertEquals(HudAltitudeMode.SEA_LEVEL, settings.hudAltitudeMode)
    }

    @Test fun heightFieldSwitchesAtLowAltitudeAndFallsBackWhenScaleIsLost() {
        val base = TelemetryParser.parse("""{"valid":true,"H, m":1200}""", """{"valid":true,"radio_altitude":500}""")!!
        var telemetry by mutableStateOf(base)
        var unit by mutableStateOf<CockpitAltitudeUnit?>(CockpitAltitudeUnit.METRES)
        var mode by mutableStateOf(HudAltitudeMode.LOW_RADAR)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics(cockpitAltitudeUnit = unit)),
                AppSettings(hudFields = listOf("altitude"), hudAltitudeMode = mode,
                    hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } } }
        compose.onNodeWithText("500 m · 雷达估计").assertExists()
        compose.runOnIdle { telemetry = base.copy(radioAltitudeRaw = 501.0) }
        compose.onNodeWithText("1200 m").assertExists()
        compose.runOnIdle { mode = HudAltitudeMode.ALWAYS_RADAR }
        compose.onNodeWithText("501 m · 雷达估计").assertExists()
        compose.runOnIdle { unit = null }
        compose.onNodeWithText("1200 m").assertExists()
        compose.runOnIdle { unit = CockpitAltitudeUnit.METRES; mode = HudAltitudeMode.SEA_LEVEL }
        compose.onNodeWithText("1200 m").assertExists()
    }

    @Test fun hudSeparatesEstimatedMetresFromRawInstrumentReading() {
        val base = TelemetryParser.parse("""{"valid":true,"H, m":1200}""", """{"valid":true,"radio_altitude":1000}""")!!
        var telemetry by mutableStateOf(base)
        var unit by mutableStateOf<CockpitAltitudeUnit?>(null)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics(cockpitAltitudeUnit = unit)),
                AppSettings(hudFields = listOf("radio_altitude_estimate", "radio_altitude_raw"),
                    hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } } }
        compose.onNodeWithText("雷达高度估计").assertExists()
        compose.onNodeWithText("1000 仪表单位").assertExists()
        compose.onNodeWithText("— m · 待判定").assertExists()
        compose.runOnIdle { unit = CockpitAltitudeUnit.FEET }
        compose.onNodeWithText("305 m · 按高度表单位推断").assertExists()
        compose.onNodeWithText("1000 仪表单位").assertExists()
        compose.runOnIdle { unit = CockpitAltitudeUnit.METRES }
        compose.onNodeWithText("1000 m · 按高度表单位推断").assertExists()
        compose.runOnIdle { telemetry = base.copy(radioAltitudeRaw = null) }
        compose.onNodeWithText("— m · 按高度表单位推断").assertExists()
        compose.onNodeWithText("— 仪表单位").assertExists()
        compose.runOnIdle { telemetry = base; unit = null }
        compose.onNodeWithText("— m · 待判定").assertExists()
        compose.onNodeWithText("305 m · 按高度表单位推断").assertDoesNotExist()
    }
}
