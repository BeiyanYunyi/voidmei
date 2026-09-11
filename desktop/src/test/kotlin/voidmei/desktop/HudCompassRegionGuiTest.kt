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
import kotlin.test.assertTrue
import voidmei.config.*
import voidmei.telemetry.*

class HudCompassRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compassResizesIndependentlyAndClearsUnavailableHeading() {
        var scene by mutableStateOf(HudSceneLayout(600, 400, listOf(
            HudRegion("compass", HudRegionContent.COMPASS, 300, 0, 240, 240, title = "导航"),
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 280, 240, fields = listOf("ias")))))
        var settings by mutableStateOf(AppSettings(hudAttitude = false))
        val original = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(headingDeg = 90.0)) }
        var connection by mutableStateOf<ConnectionState>(original)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 400.dp)) {
            HudPanel(connection, settings.copy(hudSceneLayout = scene), emptyList(), null) {}
        } } }
        compose.onNodeWithContentDescription("固定北向罗盘，航向 90°").assertIsDisplayed()
        val first = compose.onNodeWithTag("hud-compass").getUnclippedBoundsInRoot()
        assertTrue(first.left >= 312.dp)
        compose.runOnIdle { scene = scene.resizeRegion("compass", 280, 350); settings = settings.copy(hudCompassHeadingUp = true) }
        compose.onNodeWithContentDescription("航向朝上罗盘，航向 90°").assertIsDisplayed()
        val resized = compose.onNodeWithTag("hud-compass").getUnclippedBoundsInRoot()
        assertTrue(resized.bottom - resized.top > first.bottom - first.top)
        assertTrue(resized.right <= 580.dp && resized.bottom <= 350.dp)
        compose.runOnIdle { connection = original.copy(telemetry = original.telemetry.copy(headingDeg = null)) }
        compose.onNodeWithTag("hud-compass").assertDoesNotExist()
        compose.onNodeWithText("航向未知").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.onNodeWithText("航向未知").assertDoesNotExist()
        compose.onNodeWithTag("hud-compass").assertDoesNotExist()
        compose.runOnIdle { connection = original }
        compose.onNodeWithTag("hud-compass").assertIsDisplayed()
        compose.runOnIdle { scene = scene.copy(regions = scene.regions.map { if (it.id == "compass") it.copy(visible = false) else it }) }
        compose.onNodeWithTag("hud-compass").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-flight").assertIsDisplayed()
    }
}
