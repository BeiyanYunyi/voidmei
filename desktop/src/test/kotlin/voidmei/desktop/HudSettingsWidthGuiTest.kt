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

    @Test fun everyFieldCanBeSelectedInsideANarrowSettingsPanel() {
        var settings by mutableStateOf(AppSettings(hudFields = emptyList()))
        compose.setContent { MaterialTheme { Column(Modifier.width(240.dp).height(500.dp)
            .testTag("narrow-settings").verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.onNodeWithText("HUD 字段设置").performClick()
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
        compose.onNodeWithText("恢复默认").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(HudField.defaults, settings.hudFields) }
    }
}
