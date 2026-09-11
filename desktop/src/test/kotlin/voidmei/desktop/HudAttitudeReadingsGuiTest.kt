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

class HudAttitudeReadingsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun attitudeFieldsRenderWithoutHorizonAndClearMissingAxis() {
        val scene = HudSceneLayout(500, 250, listOf(HudRegion("angles", HudRegionContent.FLIGHT,
            0, 0, 500, 250, fields = listOf("pitch", "roll"))))
        val settings = AppSettings(hudSceneLayout = scene, hudAttitude = false)
        val original = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(pitchDeg = -13.177, rollDeg = -88.9)) }
        var flight by mutableStateOf(original)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 250.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("俯仰（抬头为正）").assertIsDisplayed()
        compose.onNodeWithText("13.2 °").assertIsDisplayed()
        compose.onNodeWithText("-88.9 °").assertIsDisplayed()
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(pitchDeg = null)) }
        compose.onNodeWithText("13.2 °").assertDoesNotExist()
        compose.onNodeWithText("-88.9 °").assertIsDisplayed()
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(rollDeg = null)) }
        compose.onNodeWithText("13.2 °").assertIsDisplayed()
        compose.onNodeWithText("-88.9 °").assertDoesNotExist()
    }
}
