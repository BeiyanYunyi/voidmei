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
import kotlin.test.assertEquals

class RecordingFileSelectionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun flightSelectionAndCancelPreserveExplicitLoadFlow() = checkSelection(false)
    @Test fun engineSelectionAndCancelPreserveExplicitLoadFlow() = checkSelection(true)

    @Test fun recentlyCompletedFilesOnlyReplaceDraftsWhenSelected() {
        var latest by mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingAnalysisPanel(latestFile = latest)
            EngineRecordingPanel(latestFile = latest?.removeSuffix(".csv")?.plus("-engines.csv"))
        } } }
        compose.onNodeWithText("使用最近完成的飞行记录").assertDoesNotExist()
        compose.onNodeWithText("飞行 CSV 文件路径").performTextInput("manual.csv")
        compose.onNodeWithText("旧版中文 CSV").performClick()
        compose.runOnIdle { latest = "/records/最近.csv" }
        compose.onNodeWithText("飞行 CSV 文件路径").assertTextContains("manual.csv")
        compose.onNodeWithText("使用最近完成的飞行记录").performScrollTo().performClick()
        compose.onNodeWithText("飞行 CSV 文件路径").assertTextContains("/records/最近.csv")
        compose.onNodeWithText("Kotlin CSV").assertIsSelected()
        compose.onNodeWithText("读取失败：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("发动机记录分析").performScrollTo().performClick()
        compose.onNodeWithText("使用最近完成的发动机记录").performScrollTo().performClick()
        compose.onNodeWithText("发动机 CSV 文件路径").assertTextContains("/records/最近-engines.csv")
        compose.runOnIdle { latest = "/records/下一段.csv" }
        compose.onNodeWithText("发动机 CSV 文件路径").assertTextContains("/records/最近-engines.csv")
        compose.onNodeWithText("使用最近完成的发动机记录").performScrollTo().performClick()
        compose.onNodeWithText("发动机 CSV 文件路径").assertTextContains("/records/下一段-engines.csv")
        compose.onNodeWithText("发动机记录读取失败：", substring = true).assertDoesNotExist()
    }

    private fun checkSelection(engine: Boolean) {
        var selected: String? = null
        var initial: String? = null
        val choose: (String) -> String? = { initial = it; selected }
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (engine) EngineRecordingPanel(choose) else RecordingAnalysisPanel(choose)
        } } }
        if (engine) compose.onNodeWithText("发动机记录分析").performClick()
        val pathLabel = if (engine) "发动机 CSV 文件路径" else "飞行 CSV 文件路径"
        val button = if (engine) "选择发动机文件" else "选择飞行文件"
        compose.onNodeWithText(pathLabel).performTextInput("old.csv")
        compose.onNodeWithText(button).performScrollTo().performClick()
        compose.onNodeWithText(pathLabel).assertTextContains("old.csv")
        compose.runOnIdle { assertEquals("old.csv", initial); selected = "/records/带 空格.csv" }
        compose.onNodeWithText(button).performClick()
        compose.onNodeWithText(pathLabel).assertTextContains("/records/带 空格.csv")
        compose.onNodeWithText(if (engine) "读取发动机摘要" else "读取摘要").assertIsEnabled()
        compose.onNodeWithText("读取失败：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("发动机记录读取失败：", substring = true).assertDoesNotExist()
    }
}
