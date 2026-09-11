package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.*
import voidmei.config.*

class SettingsTransferGuiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun exportUsesCurrentSettingsAndInvalidImportDoesNotOpenConfirmation() {
        val settings = AppSettings(pollIntervalMs = 80, hudFields = listOf("sep"))
        val file = temporary.root.toPath().resolve("backup.json")
        var restores = 0
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 500.dp)) {
            SettingsTransferPanel(settings, true, { restores++ },
                chooseImport = { file.toString() }, chooseExport = { file.toString() })
        } } }
        compose.onNodeWithTag("settings-backup").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("已备份设置：$file").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(settings, readSettingsBackup(file, false))
        compose.onNodeWithTag("settings-backup").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("备份失败", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(settings, readSettingsBackup(file, false))
        java.nio.file.Files.writeString(file, """{"version":1,"presets":{}}""")
        compose.onNodeWithTag("settings-restore-read").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("读取失败", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings-restore-confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, restores) }
    }

    @Test fun restoreRequiresConfirmationAndRemainsBlockedIfRecordingStarts() {
        val saved = AppSettings(pollIntervalMs = 80, hudCompatibilityMode = true, hudLabelColor = "#123456")
        val file = temporary.root.toPath().resolve("backup.json")
        writeSettingsBackup(file, saved)
        var settings by mutableStateOf(AppSettings())
        var allowed by mutableStateOf(true)
        var restores = 0
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 500.dp)) {
            SettingsTransferPanel(settings, allowed, { settings = it; restores++ },
                chooseImport = { file.toString() }, chooseExport = { null })
        } } }
        fun read() {
            compose.onNodeWithTag("settings-restore-read").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("settings-restore-confirm").fetchSemanticsNodes().isNotEmpty() }
        }
        read()
        compose.runOnIdle { assertEquals(0, restores); assertEquals(100L, settings.pollIntervalMs) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("settings-restore-confirm").assertDoesNotExist()
        read()
        compose.runOnIdle { allowed = false }
        compose.onNodeWithTag("settings-restore-confirm").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, restores); allowed = true }
        compose.onNodeWithTag("settings-restore-confirm").performClick()
        compose.runOnIdle { assertEquals(saved, settings); assertEquals(1, restores) }
        compose.onNodeWithText("已应用备份设置").assertIsDisplayed()
        compose.runOnIdle { allowed = false }
        compose.onNodeWithTag("settings-restore-read").assertIsNotEnabled()
        compose.onNodeWithTag("settings-backup").assertIsEnabled()
    }
}
