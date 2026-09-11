package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
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
import kotlin.test.*

class HudLayoutPreviewGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewOpensClosesAndFollowsSettingsWithoutEnablingTheRealHud() {
        var settings by mutableStateOf(AppSettings(hudEnabled = false, hudFields = listOf("ias"),
            hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Column { HudSettingsPanel(settings) { settings = it } } } }
        fun windows() = Window.getWindows().filterIsInstance<Frame>().filter {
            it.title == "HUD 布局预览 · 示例数据" && it.isDisplayable
        }
        compose.onNodeWithTag("hud-layout-preview").performClick()
        compose.waitUntil(5000) { windows().singleOrNull()?.isVisible == true }
        val frame = windows().single()
        compose.onNodeWithText("示例数据 · 可在设置窗口继续调整").assertIsDisplayed()
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        savePreview("initial")
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

    private fun savePreview(name: String) {
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
    }
}
