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
import voidmei.config.*
import voidmei.telemetry.*
import kotlin.test.*

class HudCrosshairRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionReplacesGlobalCrosshairAndResizesActualDrawing() {
        val base = HudSceneLayout(800, 500, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 240, 100, fields = emptyList())))
        val crosshair = HudRegion("aim", HudRegionContent.CROSSHAIR, 300, 100, 120, 120, 0f)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = base, hudCrosshair = true, hudCrosshairRight = true))
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 500.dp)) {
            HudPanel(connection, settings, emptyList(), null) {}
        } } }
        compose.onAllNodesWithTag("hud-crosshair").assertCountEquals(1)
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = base.copy(regions = base.regions + crosshair)) }
        compose.onAllNodesWithTag("hud-crosshair").assertCountEquals(1)
        val small = compose.onNodeWithTag("hud-crosshair").getUnclippedBoundsInRoot()
        assertEquals(312.dp, small.left)
        assertEquals(112.dp, small.top)
        fun paintedWidth(): Int {
            val pixels = compose.onNodeWithTag("hud-crosshair").captureToImage().toPixelMap()
            val columns = (0 until pixels.width).filter { x -> (0 until pixels.height).any { y ->
                val color = pixels[x, y]
                color.red > .8f && color.green > .6f && color.blue < .2f
            } }
            return columns.last() - columns.first()
        }
        val originalWidth = paintedWidth()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.resizeRegion("aim", 220, 220)) }
        assertTrue(paintedWidth() > originalWidth + 50)
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = base.copy(regions = base.regions + crosshair.copy(visible = false))) }
        compose.onNodeWithTag("hud-crosshair").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = base) }
        compose.onNodeWithTag("hud-crosshair").assertExists()
        compose.runOnIdle {
            settings = settings.copy(hudCrosshair = false, hudSceneLayout = base.copy(regions = base.regions + crosshair))
        }
        compose.onNodeWithTag("hud-crosshair").assertExists()
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.onNodeWithTag("hud-crosshair").assertDoesNotExist()
    }
}
