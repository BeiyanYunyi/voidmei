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
import voidmei.config.LegacySettingsReader
import kotlin.test.assertFalse

class LegacyAlphaReportGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun alphaOnlyFileExplainsWhatWasSkippedAndCannotApply() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :alpha 180)""")
        var applied = false
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(readSettings = { _, _ -> imported }) { applied = true }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("未迁移 1 项：查看").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("未迁移 1 项：查看").performScrollTo().performClick()
        compose.onNodeWithText("飞行信息的旧透明度未转换；请在分区设置中分别调整背景和内容（:alpha）").assertExists()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(applied) }
    }
}
