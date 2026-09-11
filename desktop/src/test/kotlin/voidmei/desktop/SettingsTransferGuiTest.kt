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

    @Test fun confirmedBackupUpdatesHudAndPersistsForNextLaunch() {
        val original = AppSettings(hudSceneLayout = HudSceneLayout(500, 300, listOf(
            HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 500, 300,
                engineIndex = 1, fields = listOf("rpm"), showEngineInstruments = false))))
        val restored = original.copy(hudLabelColor = "#00FF00", hudValueColor = "#0000FF",
            hudSceneLayout = original.hudSceneLayout!!.copy(regions = original.hudSceneLayout!!.regions.map {
                it.copy(engineIndex = 2, fields = listOf("water_temperature"))
            }))
        val file = temporary.root.toPath().resolve("backup.json")
        val store = SettingsStore(temporary.root.toPath().resolve("settings.json"))
        store.load()
        store.save(original)
        writeSettingsBackup(file, restored)
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 550.dp)) {
            SettingsTransferPanel(settings, true, { settings = it; store.save(it) },
                chooseImport = { file.toString() }, chooseExport = { null })
            Box(Modifier.size(500.dp, 300.dp)) {
                HudPanel(hudPreviewFlight(), settings, emptyList(), null) {}
            }
        } } }
        compose.onNodeWithText("2400 RPM").assertIsDisplayed()
        compose.onNodeWithTag("settings-restore-read").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("settings-restore-confirm").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(original, SettingsStore(store.file).load().settings)
        compose.onNodeWithTag("settings-restore-confirm").performClick()
        compose.onNodeWithText("2400 RPM").assertDoesNotExist()
        compose.onNodeWithText("发动机 #2").assertIsDisplayed()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("90.0 °C").assertIsDisplayed().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(androidx.compose.ui.graphics.Color.Blue, results.single().layoutInput.style.color)
        assertEquals(restored, SettingsStore(store.file).load().settings)
        assertEquals(restored, readSettingsBackup(file, false))
    }

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
