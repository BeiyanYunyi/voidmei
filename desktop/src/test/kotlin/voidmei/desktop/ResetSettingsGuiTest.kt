package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ResetSettingsGuiTest {
    @get:Rule val compose = createComposeRule()
    private val defaults = SettingsStore.desktopDefaults(emptyMap(), "Linux", Path.of("/home/test-user"))

    @Test fun cancellingPreservesSettingsAndConfirmingPersistsPlatformDefaults() {
        val directory = Files.createTempDirectory("voidmei-reset-settings")
        try {
            val store = SettingsStore(directory.resolve("settings.json"), defaults)
            store.load()
            val customized = defaults.copy(endpoint = "http://localhost:9222", pollIntervalMs = 80,
                hudEnabled = true, hudCompatibilityMode = false, hudNumberFont = "serif", voiceVolume = 25,
                recordingDirectory = directory.resolve("records").toString())
            store.save(customized)
            var current by mutableStateOf(customized)
            var applied = 0
            compose.setContent { MaterialTheme {
                ResetSettingsPanel(defaults) { current = it; store.save(it); applied++ }
            } }
            compose.onNodeWithText("恢复默认设置").performClick()
            compose.onNodeWithText("HUD 绘制模式：兼容 HUD").assertExists()
            compose.onNodeWithText("模型目录：", substring = true).assertExists()
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertEquals(customized, current); assertEquals(0, applied) }
            assertEquals(customized, SettingsStore(store.file).load().settings)
            compose.onNodeWithText("恢复默认设置").performClick()
            compose.onNodeWithText("确认恢复").performClick()
            compose.onNodeWithText("确认恢复默认设置").assertDoesNotExist()
            compose.runOnIdle { assertEquals(defaults, current); assertEquals(1, applied) }
            assertEquals(defaults, SettingsStore(store.file).load().settings)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun activeRecordingAndWriteFailuresPreventAnAlreadyOpenReset() {
        var recording by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var applied = 0
        compose.setContent { MaterialTheme {
            ResetSettingsPanel(defaults, enabled, recording) { applied++ }
        } }
        compose.onNodeWithText("恢复默认设置").performClick()
        compose.runOnIdle { recording = true }
        compose.onNodeWithText("确认恢复").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("恢复默认设置").assertIsNotEnabled()
        compose.onNodeWithText("请先停止记录再恢复设置。").assertExists()
        compose.runOnIdle { recording = false }
        compose.onNodeWithText("恢复默认设置").performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("确认恢复").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, applied) }
    }
}
