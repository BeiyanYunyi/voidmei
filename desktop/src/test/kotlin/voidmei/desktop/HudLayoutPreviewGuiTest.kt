package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.Robot
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import voidmei.config.AppSettings
import voidmei.telemetry.FlightAlert
import kotlin.test.*

class HudLayoutPreviewGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun machPreviewSwitchesBetweenNormalWarningAndMissingSamples() {
        var warnings by mutableStateOf(false)
        var missing by mutableStateOf(false)
        val settings = AppSettings(hudFields = listOf("mach"), hudAttitude = false,
            hudMechanization = false, hudEngineFields = emptyList())
        compose.setContent { MaterialTheme { HudLayoutPreview(settings, warnings, missing) } }
        compose.onNodeWithText("0.30", substring = true).assertIsDisplayed()
        compose.runOnIdle { warnings = true }
        compose.onNodeWithText("0.30", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0.95", substring = true).assertIsDisplayed()
        compose.runOnIdle { missing = true }
        compose.onNodeWithText("0.95", substring = true).assertDoesNotExist()
        compose.onNodeWithText("—", substring = true).assertIsDisplayed()
        compose.runOnIdle { warnings = false; missing = false }
        compose.onNodeWithText("0.30", substring = true).assertIsDisplayed()
        compose.onNodeWithText("—", substring = true).assertDoesNotExist()
    }

    @Test fun previewOpensClosesAndFollowsSettingsWithoutEnablingTheRealHud() {
        var settings by mutableStateOf(AppSettings(hudEnabled = false, hudFields = listOf("ias", "engine1_throttle"),
            textFont = "Monospaced", hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Column { HudSettingsPanel(settings) { settings = it } } } }
        fun windows() = Window.getWindows().filterIsInstance<Frame>().filter {
            it.title == "HUD 布局预览 · 示例数据" && it.isDisplayable
        }
        compose.onNodeWithTag("hud-layout-preview").performClick()
        compose.waitUntil(5000) { windows().singleOrNull()?.isVisible == true }
        val frame = windows().single()
        compose.onNodeWithText("示例数据与模型 · 可在设置窗口继续调整").assertIsDisplayed()
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        val initialImage = savePreview("initial")
        assertTrue((0 until initialImage.height).any { y -> (0 until initialImage.width).any { x ->
            initialImage.getRGB(x, y) and 0xFFFFFF == 0x84DEC6
        } }, "The preview throttle gauge must use the live HUD primary color")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("HUD 布局预览").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(resolveTextFont(settings.textFont).family, layouts.single().layoutInput.style.fontFamily)
        compose.onNodeWithText("告警示例").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("510 km/h").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("510 km/h").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.IAS_LIMIT.label))
        compose.runOnIdle { settings = settings.copy(hudWarningColor = "#00FF00") }
        savePreview("warnings")
        val bounds = frame.bounds
        compose.runOnIdle { frame.isVisible = false }
        compose.waitUntil(5000) { !frame.isVisible }
        compose.onNodeWithTag("hud-layout-preview").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { frame.isVisible }
        compose.runOnIdle { assertSame(frame, windows().single()); assertEquals(bounds, frame.bounds) }
        compose.onNodeWithText("510 km/h").assertIsDisplayed()
        compose.onNodeWithText("缺失数据").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("— km/h").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("— km/h").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("flight-alerts").assertDoesNotExist()
        savePreview("missing")
        compose.runOnIdle { settings = settings.copy(hudWidthDp = 240) }
        compose.waitUntil(5000) { frame.width == 240 }
        compose.onNodeWithText("缺失数据").assertIsDisplayed()
        savePreview("missing-narrow")
        compose.onNodeWithText("正常读数").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("340 km/h").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("340 km/h").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle {
            assertFalse(settings.hudEnabled)
            settings = settings.copy(hudFields = listOf("altitude"), hudEngineIndex = 2,
                hudEngineFields = listOf("water_temperature"), hudReadingColumns = 1,
                hudFontScale = 1.5f, hudWidthDp = 500)
        }
        compose.waitUntil(5000) { frame.width == 500 }
        compose.waitUntil(5000) { compose.onAllNodesWithText("340 km/h").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("340 km/h").assertDoesNotExist()
        compose.onNodeWithText("1500 m").assertIsDisplayed()
        compose.onNodeWithText("发动机 #2").assertIsDisplayed()
        compose.onNodeWithText("90.0 °C").assertIsDisplayed()
        savePreview("configured")
        compose.runOnIdle { assertSame(frame, windows().single()); frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) }
        compose.waitUntil(5000) { windows().isEmpty() }
        compose.onNodeWithTag("hud-layout-preview").performClick()
        compose.waitUntil(5000) { windows().singleOrNull()?.isVisible == true }
        compose.onNodeWithText("1500 m").assertIsDisplayed()
        compose.runOnIdle { assertFalse(settings.hudEnabled) }
    }

    private fun savePreview(name: String): java.awt.image.BufferedImage {
        val frame = Window.getWindows().filterIsInstance<Frame>().single {
            it.title == "HUD 布局预览 · 示例数据" && it.isVisible
        }
        compose.runOnIdle { frame.toFront() }
        val robot = Robot()
        robot.waitForIdle()
        robot.delay(150) // Let the separate window's compositor present its frame.
        val output = robot.createScreenCapture(frame.bounds)
        val directory = Path.of("build/hud-preview")
        Files.createDirectories(directory)
        check(ImageIO.write(output, "png", directory.resolve("layout-preview-$name.png").toFile()))
        return output
    }
}
