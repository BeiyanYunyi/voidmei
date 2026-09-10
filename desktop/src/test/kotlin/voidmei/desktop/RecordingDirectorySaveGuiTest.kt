package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class RecordingDirectorySaveGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savesWithoutCreatingDirectoryAndPreservesSavedPathOnInvalidInput() {
        val root = Files.createTempDirectory("voidmei-directory-setting")
        val target = root.resolve("records")
        var draft by mutableStateOf(root.resolve("unused/../records").toString())
        var saved by mutableStateOf("old")
        var enabled by mutableStateOf(true)
        try {
            compose.setContent { MaterialTheme { Column {
                RecordingDirectorySave(saved, draft, enabled) { saved = it }
            } } }
            compose.onNodeWithText("已配置的记录目录：old").assertExists()
            compose.onNodeWithText("保存记录目录").performClick()
            compose.onNodeWithText("已配置的记录目录：$target").assertExists()
            assertFalse(Files.exists(target))
            compose.runOnIdle { draft = "invalid\u0000path" }
            compose.onNodeWithText("保存记录目录").performClick()
            compose.onNodeWithText("目录路径无效：", substring = true).assertExists()
            compose.onNodeWithText("已配置的记录目录：$target").assertExists()
            compose.runOnIdle { draft = "next"; enabled = false }
            compose.onNodeWithText("保存记录目录").assertIsNotEnabled()
            compose.runOnIdle { enabled = true; draft = " " }
            compose.onNodeWithText("保存记录目录").assertIsNotEnabled()
        } finally { Files.delete(root) }
    }
}
