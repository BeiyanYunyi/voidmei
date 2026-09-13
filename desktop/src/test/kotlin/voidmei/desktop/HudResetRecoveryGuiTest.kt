package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.HudField
import kotlin.test.*

class HudResetRecoveryGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedResetAndPanelCollapseRetainThePriorLayoutWithoutRevertingOtherSettings() {
        val original = AppSettings(hudFields = listOf("fuel_loss_rate", "sep"), hudWidthDp = 680,
            hudFontScale = 1.5f, hudReadingColumns = 1, hudValueColor = "#00FF00")
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("reset")
        compose.onNodeWithText("恢复默认").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(HudField.defaults, settings.hudFields); assertEquals(440, settings.hudWidthDp) }
        compose.onNodeWithText("恢复默认").performScrollTo().performClick()
        compose.onNodeWithTag("settings-back-HUD 布局").performScrollTo().performClick()
        compose.runOnIdle { settings = settings.copy(pollIntervalMs = 80, hudEnabled = true, recordingAutoStart = true) }
        compose.openHudSettingsPage("reset")
        compose.onNodeWithText("恢复重置前布局").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original.copy(pollIntervalMs = 80, hudEnabled = true, recordingAutoStart = true), settings) }
        compose.onNodeWithText("恢复重置前布局").assertDoesNotExist()
    }
}
