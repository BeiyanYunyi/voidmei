package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.HudField

class HudSettingsWidthGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pointerHelpTracksSceneModeAndRetainsVerticalPreference() {
        var settings by mutableStateOf(AppSettings(hudClickThrough = false))
        compose.setContent { MaterialTheme { Column(Modifier.width(450.dp).height(600.dp)
            .verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("behavior")
        compose.onNodeWithTag("hud-click-through").performScrollTo().assertIsOff().assertIsEnabled()
        compose.openHudSettingsPage("regions")
        compose.onNodeWithTag("hud-scene-toggle").performScrollTo().performClick()
        compose.openHudSettingsPage("behavior")
        compose.onNodeWithTag("hud-click-through").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("hud-click-through-help").assertTextEquals(
            "分区模式始终穿透鼠标，请在预览中调整区域。返回纵向 HUD 布局后可关闭穿透。")
        compose.runOnIdle { assertFalse(settings.hudClickThrough) }
        compose.openHudSettingsPage("regions")
        compose.onNodeWithTag("hud-scene-toggle").performScrollTo().performClick()
        compose.openHudSettingsPage("behavior")
        compose.onNodeWithTag("hud-click-through").performScrollTo().assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("hud-click-through-help").assertTextEquals(
            "开启后鼠标操作下方窗口，HUD 无法拖动或点击关闭。可在此关闭穿透以重新调整 HUD。")
    }

    @Test fun everyFieldCanBeSelectedInsideANarrowSettingsPanel() {
        var settings by mutableStateOf(AppSettings(hudFields = emptyList()))
        compose.setContent { MaterialTheme { Column(Modifier.width(240.dp).height(500.dp)
            .testTag("narrow-settings").verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("fields")
        val bounds = compose.onNodeWithTag("narrow-settings").getUnclippedBoundsInRoot()
        for (field in HudField.entries) {
            val button = compose.onNodeWithTag("hud-add-${field.id}")
            button.performScrollTo().assertIsDisplayed()
            val box = button.getUnclippedBoundsInRoot()
            assertTrue(box.left >= bounds.left && box.right <= bounds.right,
                "${field.label}: $box exceeds $bounds")
        }
        compose.onNodeWithTag("hud-add-rudder").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("rudder"), settings.hudFields) }
        compose.openHudSettingsPage("reset")
        compose.onNodeWithText("恢复默认").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(HudField.defaults, settings.hudFields) }
    }
}
