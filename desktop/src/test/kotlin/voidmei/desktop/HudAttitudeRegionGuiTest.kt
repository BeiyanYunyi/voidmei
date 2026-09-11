package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import voidmei.config.*
import voidmei.telemetry.*

class HudAttitudeRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun horizonUsesRemainingSpaceAndPreservesPitchColors() {
        var region by mutableStateOf(HudRegion("attitude", HudRegionContent.ATTITUDE, 0, 0, 400, 240))
        var fontScale by mutableStateOf(1f)
        val original = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(pitchDeg = -20.0, rollDeg = 0.0,
            angleOfAttackDeg = null, sideslipAngleDeg = null)) }
        var flight by mutableStateOf(original)
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 550.dp)) {
            HudPanel(flight, AppSettings(hudSceneLayout = HudSceneLayout(400, 550, listOf(region)),
                hudFontScale = fontScale), emptyList(), null) {}
        } } }
        val initial = compose.onNodeWithTag("attitude-canvas").getUnclippedBoundsInRoot()
        compose.runOnIdle { region = region.copy(height = 500) }
        val enlarged = compose.onNodeWithTag("attitude-canvas").getUnclippedBoundsInRoot()
        assertTrue(enlarged.bottom - enlarged.top > initial.bottom - initial.top + 200.dp)
        compose.runOnIdle { region = region.copy(title = "姿态与航向".repeat(10)); fontScale = 1.5f }
        val titled = compose.onNodeWithTag("attitude-canvas").getUnclippedBoundsInRoot()
        assertTrue(titled.bottom - titled.top > 100.dp)
        assertTrue(titled.bottom <= 488.dp && titled.top >= 12.dp)
        compose.onNodeWithText("俯仰 20.0°", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        fun centerBackgroundIsSky(): Boolean {
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            val color = pixels[5, pixels.height / 2]
            return color.blue > color.red
        }
        assertTrue(centerBackgroundIsSky())
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(pitchDeg = 20.0)) }
        assertTrue(!centerBackgroundIsSky())
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(pitchDeg = null)) }
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
        compose.onNodeWithText("姿态数据不可用").assertIsDisplayed()
        compose.runOnIdle { flight = original; fontScale = 1f; region = region.copy(title = "", height = 240) }
        val restored = compose.onNodeWithTag("attitude-canvas").getUnclippedBoundsInRoot()
        assertTrue(restored == initial)
    }
}
