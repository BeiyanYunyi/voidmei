package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*

class HudEngineThrottleGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun engineBarsTrackTheirOwnThrottleAndDisappearWithUnavailableOrDeselectedData() {
        val first = hudPreviewFlight().telemetry.engines.first().copy(index = 1, throttlePercent = 55.0)
        var engines by mutableStateOf(listOf(first, first.copy(index = 2, throttlePercent = 120.0)))
        var fields by mutableStateOf(listOf(HudEngineField.THROTTLE))
        compose.setContent { MaterialTheme { Column(Modifier.size(500.dp, 350.dp)) {
            HudEnginePanel(engines, 1, fields = fields)
            HudEnginePanel(engines, 2, fields = fields)
        } } }
        compose.onNodeWithTag("hud-engine-throttle-1").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.onNodeWithTag("hud-engine-throttle-2").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        compose.onNodeWithText("120 %").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 号油门，满刻度 110%").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "油门超过 100%"))
        compose.runOnIdle { engines = engines.map { it.copy(throttlePercent = if (it.index == 1) -1.0 else 0.0) } }
        compose.onNodeWithTag("hud-engine-throttle-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-throttle-2").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle { engines = listOf(first, first) }
        compose.onNodeWithTag("hud-engine-throttle-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-throttle-2").assertDoesNotExist()
        compose.runOnIdle { engines = listOf(first); fields = listOf(HudEngineField.RPM) }
        compose.onNodeWithTag("hud-engine-throttle-1").assertDoesNotExist()
        compose.runOnIdle { fields = listOf(HudEngineField.THROTTLE) }
        compose.onNodeWithTag("hud-engine-throttle-1").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
    }
}
