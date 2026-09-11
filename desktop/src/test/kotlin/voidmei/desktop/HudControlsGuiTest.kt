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
import kotlin.test.*
import voidmei.config.*

class HudControlsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorSelectsAxesIndependentlyAndPreservesUnknownIds() {
        val one = HudRegion("one", HudRegionContent.CONTROLS, 0, 0, 400, 260, fields = listOf("aileron", "future"))
        var scene by mutableStateOf(HudSceneLayout(800, 260, listOf(one, one.copy(id = "two", x = 400, fields = listOf("rudder")))))
        compose.setContent { MaterialTheme { Column {
            HudRegionFieldsSettings(scene.regions.first(), AppSettings()) { region ->
                scene = scene.copy(regions = scene.regions.map { if (it.id == region.id) region else it })
            }
            Box(Modifier.size(800.dp, 260.dp)) { HudPanel(hudPreviewFlight(), AppSettings(hudSceneLayout = scene), emptyList(), null) {} }
        } } }
        compose.onNodeWithTag("hud-control-aileron").assertIsDisplayed()
        compose.onNodeWithTag("hud-control-rudder").assertIsDisplayed()
        compose.onNodeWithTag("hud-control-elevator").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-control-one-aileron").performClick()
        compose.onNodeWithText("未选择操纵面").assertIsDisplayed()
        compose.onNodeWithTag("hud-control-aileron").assertDoesNotExist()
        compose.onNodeWithTag("hud-control-rudder").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-control-one-elevator").performClick()
        compose.onNodeWithText("升降舵 20.0%").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("future", "elevator"), scene.regions.first().fields)
            assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
            scene = scene.copy(regions = scene.regions.map { if (it.id == "one") it.copy(fields = null) else it })
        }
        compose.onNodeWithTag("hud-control-aileron").assertIsDisplayed()
        compose.onAllNodesWithTag("hud-control-rudder").assertCountEquals(2)
    }

    @Test fun signedControlPositionsMoveAcrossScaleAndMissingInputClearsMarker() {
        val scene = HudSceneLayout(440, 300, listOf(HudRegion("controls", HudRegionContent.CONTROLS, 0, 0, 440, 300)))
        var flight by mutableStateOf(hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(
            aileronPercent = -100.0, elevatorPercent = 0.0, rudderPercent = 100.0)) })
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 300.dp)) {
            HudPanel(flight, AppSettings(hudSceneLayout = scene), emptyList(), null) {}
        } } }
        fun markerX(id: String): Double? {
            val pixels = compose.onNodeWithTag("hud-control-$id").captureToImage().toPixelMap()
            val xs = mutableListOf<Int>()
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.green > .8f && c.red in .45f.. .6f && c.blue in .7f.. .85f) xs += x
            }
            return xs.takeIf { it.isNotEmpty() }?.average()
        }
        compose.onNodeWithText("副翼 -100.0%").assertIsDisplayed()
        compose.onNodeWithText("升降舵 0.0%").assertIsDisplayed()
        compose.onNodeWithText("方向舵 100.0%").assertIsDisplayed()
        assertTrue(markerX("aileron")!! < markerX("elevator")!!)
        assertTrue(markerX("elevator")!! < markerX("rudder")!!)
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(aileronPercent = null, elevatorPercent = 101.0, rudderPercent = Double.NaN)) }
        for (id in listOf("aileron", "elevator", "rudder")) assertNull(markerX(id))
        compose.onNodeWithText("副翼 —%").assertIsDisplayed()
    }
}
