package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.ConnectionState

class HudFontScaleGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preferenceCanBeAdjustedAndReset() {
        var settings by mutableStateOf(AppSettings(hudFields = emptyList()))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("layout")
        compose.onNodeWithTag("hud-width").performSemanticsAction(SemanticsActions.SetProgress) { it(680f) }
        compose.onNodeWithText("HUD 宽度 680 dp").assertIsDisplayed()
        compose.runOnIdle { assertEquals(680, settings.hudWidthDp) }
        compose.openHudSettingsPage("font")
        compose.onNodeWithTag("hud-font-scale").performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
        compose.onNodeWithText("全局 HUD 文字大小 150%").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1.5f, settings.hudFontScale) }
        compose.openHudSettingsPage("engine")
        compose.onNodeWithText("发动机读数").performScrollTo().performClick()
        compose.onNodeWithText("HUD 发动机编号").performTextReplacement("10")
        compose.runOnIdle { assertEquals(10, settings.hudEngineIndex) }
        compose.onNodeWithText("HUD 发动机编号").performTextReplacement("0")
        compose.onNodeWithText("请输入正整数；仍显示上次有效编号。").assertExists()
        compose.runOnIdle { assertEquals(10, settings.hudEngineIndex) }
        compose.openHudSettingsPage("reset")
        compose.onNodeWithText("恢复默认").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1f, settings.hudFontScale); assertNull(settings.hudEngineIndex); assertEquals(440, settings.hudWidthDp) }
    }

    @Test fun hudScalingMultipliesSystemFontScaleWithoutChangingOtherTextOrPixelDensity() {
        var scale by mutableStateOf(1f)
        var insideDensity = 0f
        var insideFontScale = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                MaterialTheme { Column {
                    Text("主窗口文字")
                    Box(Modifier.requiredSize(440.dp, 300.dp)) {
                        HudPanel(ConnectionState.Connecting, AppSettings(hudFontScale = scale), emptyList(), null) {
                            insideDensity = LocalDensity.current.density
                            insideFontScale = LocalDensity.current.fontScale
                            Text("HUD")
                        }
                    }
                } }
            }
        }
        val outside = compose.onNodeWithText("主窗口文字").getUnclippedBoundsInRoot()
        val normal = compose.onNodeWithText("HUD").getUnclippedBoundsInRoot()
        compose.runOnIdle { scale = 2f }
        val larger = compose.onNodeWithText("HUD").getUnclippedBoundsInRoot()
        assertTrue(larger.bottom - larger.top > (normal.bottom - normal.top) * 1.8f)
        assertEquals(outside, compose.onNodeWithText("主窗口文字").getUnclippedBoundsInRoot())
        compose.runOnIdle {
            assertEquals(1f, insideDensity)
            assertEquals(2.5f, insideFontScale)
        }
    }
}
