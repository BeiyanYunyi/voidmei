package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.*

class ControlWingSweepGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun layoutPreviewShowsSweepAndClearsItsMissingSample() {
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(400, 300, listOf(
            HudRegion("controls", HudRegionContent.CONTROLS, 0, 0, 400, 300, fields = listOf("wing_sweep")))))
        var missing by mutableStateOf(false)
        var warnings by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 300.dp)) {
            HudLayoutPreview(settings, warnings = warnings, missing = missing)
        } } }
        compose.onNodeWithText("后掠 35.0%").assertIsDisplayed()
        compose.runOnIdle { warnings = true }
        compose.onNodeWithText("后掠 35.0%").assertIsDisplayed()
        compose.runOnIdle { missing = true }
        compose.onNodeWithText("后掠 35.0%").assertDoesNotExist()
        compose.onNodeWithText("后掠 —%").assertIsDisplayed()
        compose.runOnIdle { missing = false }
        compose.onNodeWithText("后掠 35.0%").assertIsDisplayed()
    }

    @Test fun selectingSweepUsesUnsignedScaleAndClearsInvalidSamples() {
        var region by mutableStateOf(HudRegion("controls", HudRegionContent.CONTROLS, 0, 0, 400, 400,
            fields = emptyList()))
        var flight by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 800.dp)) {
            HudRegionFieldsSettings(region, AppSettings()) { region = it }
            Box(Modifier.size(400.dp, 400.dp)) {
                HudPanel(flight, AppSettings(hudSceneLayout = HudSceneLayout(400, 400, listOf(region))), emptyList(), null) {}
            }
        } } }
        compose.onNodeWithTag("hud-region-control-controls-wing_sweep").performClick()
        fun sample(value: Double?) = compose.runOnIdle {
            flight = flight.copy(telemetry = flight.telemetry.copy(wingSweepRatio = value))
        }
        fun markerX(): Double? {
            val p = compose.onNodeWithTag("hud-control-wing_sweep").captureToImage().toPixelMap()
            val positions = buildList {
                for (y in 0 until p.height) for (x in 0 until p.width)
                    if (p[x, y] == Color(0xFF84DEC6)) add(x.toDouble() / p.width)
            }
            return positions.takeIf { it.isNotEmpty() }?.average()
        }
        sample(0.0)
        compose.onNodeWithText("后掠 0.0%").assertIsDisplayed()
        compose.onNodeWithContentDescription("后掠，刻度 0% 至 100%").assertExists()
        assertTrue(markerX()!! < .1)
        sample(.5)
        assertTrue(markerX()!! in .45.. .55)
        sample(1.0)
        assertTrue(markerX()!! > .9)
        for (invalid in listOf(null, -.1, 1.1, Double.NaN)) {
            sample(invalid)
            compose.onNodeWithText("后掠 —%").assertIsDisplayed()
            assertNull(markerX())
        }
        sample(.25)
        compose.onNodeWithText("后掠 25.0%").assertIsDisplayed()
        compose.runOnIdle {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(400, 400, listOf(region)))
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        compose.onNodeWithTag("hud-region-control-controls-wing_sweep").performClick()
        compose.onNodeWithTag("hud-control-wing_sweep").assertDoesNotExist()
    }
}
