package voidmei.desktop

import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.*
import voidmei.config.*

class SettingsBackupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun backupRoundTripsSettingsWithoutOverwritingExistingFiles() {
        val scene = HudSceneLayout.initial(AppSettings(hudEngineIndex = 4))
        val settings = AppSettings(endpoint = "http://localhost:8112", pollIntervalMs = 80,
            hudEnabled = true, hudCompatibilityMode = true, hudLabelColor = "#123456", hudNumberFont = "serif",
            hudFields = listOf("sep", "future-field"), hudSceneLayout = scene,
            hudScenePresets = mapOf("战斗" to scene), mainPosition = WindowPosition(25f, 30f),
            recordingDirectory = "记录", voicePack = "default")
        val file = temporary.root.toPath().resolve("backup.json")
        writeSettingsBackup(file, settings)
        val bytes = Files.readAllBytes(file)
        assertEquals(settings, readSettingsBackup(file, false))
        assertFails { writeSettingsBackup(file, AppSettings()) }
        assertContentEquals(bytes, Files.readAllBytes(file))
        assertEquals(listOf("backup.json"), temporary.root.list()!!.toList())
    }

    @Test fun rejectsUnrelatedMalformedOversizedOrInvalidEndpointDocuments() {
        val file = temporary.newFile().toPath()
        for (bytes in listOf(
            """{"version":1,"presets":{}}""".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(SettingsStore.MAX_BYTES + 1) { 32 },
            SettingsJson.encode(AppSettings(endpoint = "file:///tmp/test")).toByteArray(),
            "{".toByteArray())) {
            Files.write(file, bytes)
            assertFails { readSettingsBackup(file, true) }
            assertContentEquals(bytes, Files.readAllBytes(file))
        }
        assertFails { readSettingsBackup(temporary.root.toPath(), true) }
    }
}
