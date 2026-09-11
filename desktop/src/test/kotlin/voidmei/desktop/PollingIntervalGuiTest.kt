package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class PollingIntervalGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customIntervalRequiresApplyRejectsInvalidValuesAndTracksExternalChanges() {
        var interval by mutableStateOf(100L)
        compose.setContent { MaterialTheme { Column {
            PollingIntervalSettings(interval) { interval = it }
        } } }
        compose.onNodeWithText("自定义刷新间隔 (ms)").performTextReplacement("80")
        compose.runOnIdle { assertEquals(100L, interval) }
        compose.onNodeWithText("应用刷新间隔").performClick()
        compose.runOnIdle { assertEquals(80L, interval) }
        for (invalid in listOf("", "9", "5001", "80.5", "9223372036854775808")) {
            compose.onNodeWithText("自定义刷新间隔 (ms)").performTextReplacement(invalid)
            compose.onNodeWithText("应用刷新间隔").performClick()
            compose.onNodeWithText("请输入 10–5000 范围内的整数毫秒").assertExists()
            compose.runOnIdle { assertEquals(80L, interval) }
        }
        compose.onNodeWithText("500 ms").performClick()
        compose.onNodeWithText("自定义刷新间隔 (ms)").assertTextContains("500")
        compose.onNodeWithText("请输入 10–5000 范围内的整数毫秒").assertDoesNotExist()
        compose.runOnIdle { interval = 5000 }
        compose.onNodeWithText("自定义刷新间隔 (ms)").assertTextContains("5000")
        compose.onNodeWithText("超过 2 秒的采样间隔", substring = true).assertExists()
        compose.onNodeWithText("自定义刷新间隔 (ms)").performTextReplacement("10")
        compose.onNodeWithText("应用刷新间隔").performClick()
        compose.runOnIdle { assertEquals(10L, interval) }
        compose.onNodeWithText("超过 2 秒的采样间隔", substring = true).assertDoesNotExist()
    }
}
