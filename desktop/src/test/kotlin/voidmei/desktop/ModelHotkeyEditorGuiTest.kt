package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class ModelHotkeyEditorGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rejectsHudConflictAndCanChooseUnmodifiedKey() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { Column { ModelHotkeyEditor(settings, true) { settings = it } } } }
        compose.onNodeWithTag("model-hotkey-key").performClick()
        compose.onNodeWithTag("model-hotkey-key-H").performScrollTo().performClick()
        compose.onNodeWithText("Ctrl+Shift+H 已用于 HUD；请选其他组合。").assertExists()
        compose.runOnIdle { assertEquals("Ctrl+Shift+M", settings.modelWindowHotkey) }
        compose.onNodeWithTag("model-hotkey-key").performClick()
        compose.onNodeWithTag("model-hotkey-key-P").performScrollTo().performClick()
        compose.onNodeWithTag("model-hotkey-ctrl").performClick()
        compose.onNodeWithTag("model-hotkey-shift").performClick()
        compose.runOnIdle { assertEquals("P", settings.modelWindowHotkey); assertFalse(settings.modelWindowHotkeyEnabled) }
    }
    @Test fun previewRequiresOptInAndDoesNotEnableListening() {
        val parsed = LegacySettingsReader.read("""(panel p (item x :target displayFmKey :type hotkey :value 25))""")
        var settings = AppSettings()
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(null, readSettings = { _, _ -> parsed }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-model-hotkey").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithTag("legacy-model-hotkey").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("P", settings.modelWindowHotkey); assertFalse(settings.modelWindowHotkeyEnabled) }
    }
}
