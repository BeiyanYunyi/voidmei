package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.file.Files
import java.nio.file.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.math.min
import kotlin.test.*

class HudAttitudeNorthPointerGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothLayoutsShowCorrectNorthAndClearPointerOnMissingHeading() {
        var scene by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var earthFixed by mutableStateOf(false)
        var heading by mutableStateOf<Double?>(0.0)
        val original = hudPreviewFlight()
        compose.setContent { MaterialTheme { Box(Modifier.size(480.dp, 700.dp)) {
            val flight = original.copy(telemetry = original.telemetry.copy(headingDeg = heading, pitchDeg = -10.0,
                rollDeg = 45.0, angleOfAttackDeg = null, sideslipAngleDeg = null))
            HudPanel(flight, AppSettings(hudFields = emptyList(), hudMechanization = false,
                hudAttitudeNorthPointer = enabled, hudAttitudeEarthFixed = earthFixed,
                hudSceneLayout = if (scene) HudSceneLayout(460, 320, listOf(
                    HudRegion("attitude", HudRegionContent.ATTITUDE, 0, 0, 460, 320))) else null), emptyList(), null) {}
        } } }
        fun assertNorth(dx: Int, dy: Int) {
            if (!scene) compose.onNodeWithTag("attitude-canvas").performScrollTo()
            compose.onNodeWithTag("attitude-canvas").assertIsDisplayed()
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            val distance = min(pixels.width, pixels.height) / 8
            val x = pixels.width / 2 + dx * distance
            val y = pixels.height / 2 + dy * distance
            val red = Color(0xFFFF6577)
            assertTrue((-1..1).any { offset -> pixels[x + offset, y] == red || pixels[x, y + offset] == red })
        }
        for (useScene in listOf(false, true)) {
            compose.runOnIdle { scene = useScene; enabled = true; heading = 0.0; earthFixed = false }
            assertNorth(0, -1)
            if (useScene) {
                val output = Path.of("build/hud-preview/attitude-north-pointer.png")
                Files.createDirectories(output.parent)
                Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-region-attitude")
                    .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
            }
            compose.runOnIdle { heading = 90.0; earthFixed = true }
            assertNorth(-1, 0)
            compose.runOnIdle { heading = 180.0 }
            assertNorth(0, 1)
            compose.runOnIdle { heading = 270.0 }
            assertNorth(1, 0)
            compose.runOnIdle { heading = null }
            compose.onNodeWithText("指北针不可用：航向未知").assertExists()
            compose.onNodeWithContentDescription("红色指北针", substring = true).assertDoesNotExist()
            compose.runOnIdle { enabled = false; heading = 0.0 }
            compose.onNodeWithText("红色指北，白色指南").assertDoesNotExist()
            compose.onNodeWithContentDescription("红色指北针", substring = true).assertDoesNotExist()
        }
    }

    @Test fun sceneAttitudeCanEnablePointerWhenVerticalAttitudeIsOff() {
        var settings by mutableStateOf(AppSettings(hudAttitude = false, hudSceneLayout = HudSceneLayout(460, 320,
            listOf(HudRegion("attitude", HudRegionContent.ATTITUDE, 0, 0, 460, 320)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.onNodeWithText("HUD 字段设置").performScrollTo().performClick()
        compose.onNodeWithText("姿态图指北针").performScrollTo().performClick()
        compose.onNodeWithText("姿态：机体参考").performScrollTo().performClick()
        compose.onNodeWithText("姿态图迎角极限线").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(settings.hudAttitudeNorthPointer)
            assertFalse(settings.hudAttitude)
            assertTrue(settings.hudAttitudeEarthFixed)
            assertFalse(settings.hudAttitudeAoaLimits)
        }
    }
}
