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

class LegacyModelWindowsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun enablePreviewRequiresSelectionAndSeparatesImmediateVisibility() {
        val parsed = LegacySettingsReader.read("""(panel p (item m :target enableFMPrint :type switch :value true))""")
        var settings = AppSettings()
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(null, readSettings = { _, _ -> parsed }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-model-windows").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithTag("legacy-model-windows-show").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("legacy-model-windows").performScrollTo().performClick()
        compose.onNodeWithTag("legacy-model-windows-show").assertIsEnabled().assertIsOff()
        compose.onNodeWithTag("legacy-model-windows-show").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(settings.modelWindowEnabled); assertTrue(settings.modelJetWindowEnabled)
            assertTrue(settings.modelJetWindowAutoClose); assertFalse(settings.modelWindowHotkeyEnabled)
        }
    }
    @Test fun disablePreviewClosesModelWindowsAndListener() {
        val parsed = LegacySettingsReader.read("""(panel p (item m :target enableFMPrint :type switch :value false))""")
        var settings = AppSettings(modelWindowEnabled = true, modelJetWindowEnabled = true, modelWindowHotkeyEnabled = true)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(null, readSettings = { _, _ -> parsed }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-model-windows").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("legacy-model-windows-show").assertDoesNotExist()
        compose.onNodeWithTag("legacy-model-windows").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(settings.modelWindowEnabled); assertFalse(settings.modelJetWindowEnabled); assertFalse(settings.modelWindowHotkeyEnabled) }
    }
}
