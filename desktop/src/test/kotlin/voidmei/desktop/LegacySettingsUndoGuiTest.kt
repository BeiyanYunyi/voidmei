package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class LegacySettingsUndoGuiTest {
    @get:Rule val compose = createComposeRule()
    private fun imported(interval: Int) = LegacySettingsReader.read("""(panel p
        (item x :target dataPollIntervalMs :type slider :value $interval)
        (item volume :target voiceVolume :type slider :value 70))""")
    private fun applyPreview() {
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("应用预览设置").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
    }

    @Test fun noOpImportAndCollapsingKeepUndoWhileLaterChangesSurvive() {
        val original = AppSettings()
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsImport(current, readSettings = { _, _ -> imported(80) }) { current = it }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        applyPreview()
        compose.runOnIdle { assertEquals(original.copy(pollIntervalMs = 80, voiceVolume = 70), current) }
        applyPreview() // Same values must not discard the original recovery point.
        compose.onNodeWithText("导入旧版设置").performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(pollIntervalMs = 120, hudEnabled = true) }
        compose.onNodeWithText("可恢复 1 项；1 项当前值已不同于导入值，将保留。").assertIsDisplayed()
        compose.onNodeWithText("撤回上次旧设置导入").performClick()
        compose.runOnIdle { assertEquals(original.copy(pollIntervalMs = 120, hudEnabled = true), current) }
        compose.onNodeWithText("撤回上次旧设置导入").assertDoesNotExist()
    }

    @Test fun failedApplyAndFailedUndoKeepExistingRecoveryPoint() {
        val original = AppSettings()
        var current by mutableStateOf(original)
        var fail = false
        var interval = 80
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsImport(current, readSettings = { _, _ -> imported(interval) }) {
                    require(!fail) { "模拟应用失败" }
                    current = it
                }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        applyPreview()
        compose.runOnIdle { fail = true; interval = 90 }
        applyPreview()
        compose.onNodeWithText("应用失败：模拟应用失败").assertExists()
        compose.onNodeWithText("撤回上次旧设置导入").performScrollTo().performClick()
        compose.onNodeWithText("撤回失败：模拟应用失败").assertExists()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("撤回上次旧设置导入").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, current) }
    }
}
