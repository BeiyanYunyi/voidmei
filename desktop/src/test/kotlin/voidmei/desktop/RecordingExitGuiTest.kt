package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties

class RecordingExitGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longErrorCanScrollAndCopyWithoutHidingActions() {
        val reason = "Disk full\n" + (1..100).joinToString("\n") { "/长路径/记录目录/$it/flight.csv" }
        var copied: String? = null
        compose.setContent { MaterialTheme {
            RecordingExitDialog(reason, {}, {}, copyText = { copied = it })
        } }
        compose.onNodeWithText("返回检查").assertIsDisplayed()
        compose.onNodeWithText("仍然退出").assertIsDisplayed()
        compose.onNodeWithText("复制错误信息").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(reason, copied) }
        compose.onNodeWithTag("recording-exit-details").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 200f) }
        compose.waitForIdle()
        val range = compose.onNodeWithTag("recording-exit-details").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.value() > 0)
        compose.onNodeWithText("仍然退出").assertIsDisplayed()
    }

    @Test fun failureCanReturnForInspectionOrExplicitlyExit() {
        var shown by mutableStateOf(true)
        var exits = 0
        compose.setContent { MaterialTheme {
            if (shown) RecordingExitDialog("Disk full", { shown = false }, { exits++; shown = false })
        } }
        compose.onNodeWithText("Disk full", substring = true).assertExists()
        compose.onNodeWithText("返回检查").performClick()
        compose.onNodeWithText("记录未正常完成").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits); shown = true }
        compose.onNodeWithText("仍然退出").performClick()
        compose.onNodeWithText("记录未正常完成").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, exits) }
    }
}
