package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.*

class ControlStickGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scenePlotsSignedAxesAndClearsIncompletePositionWithoutHidingValidReadings() {
        var flight by mutableStateOf(hudPreviewFlight())
        val region = HudRegion("controls", HudRegionContent.CONTROLS, 0, 0, 400, 500,
            fields = listOf("aileron", "elevator"), showControlStick = true)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(400, 500, listOf(region))))
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 500.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        fun point(a: Double?, e: Double?) = compose.runOnIdle {
            flight = flight.copy(telemetry = flight.telemetry.copy(aileronPercent = a, elevatorPercent = e))
        }
        fun markerPositions(): List<Pair<Int, Int>> {
            val pixels = compose.onNodeWithTag("hud-control-stick").captureToImage().toPixelMap()
            return buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                    if (pixels[x, y] == Color(0xFF84DEC6)) add(x - pixels.width / 2 to y - pixels.height / 2)
            }
        }
        point(-50.0, 50.0)
        assertTrue(markerPositions().let { it.isNotEmpty() && it.all { (x, y) -> x < 0 && y > 0 } })
        point(50.0, -50.0)
        assertTrue(markerPositions().let { it.isNotEmpty() && it.all { (x, y) -> x > 0 && y < 0 } })
        point(null, -50.0)
        assertTrue(markerPositions().isEmpty())
        compose.onNodeWithTag("hud-control-stick").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "数据不可用"))
        compose.onNodeWithText("升降舵 -50.0%").assertIsDisplayed()
        point(0.0, 0.0)
        assertTrue(markerPositions().isNotEmpty())
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(
            regions = listOf(region.copy(fields = listOf("elevator"))))) }
        compose.onNodeWithTag("hud-control-stick").assertDoesNotExist()
        compose.onNodeWithText("升降舵 0.0%").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(
            regions = listOf(region.copy(showControlStick = false)))) }
        compose.onNodeWithTag("hud-control-stick").assertDoesNotExist()
        compose.onNodeWithTag("hud-control-aileron").assertExists()
    }
}
