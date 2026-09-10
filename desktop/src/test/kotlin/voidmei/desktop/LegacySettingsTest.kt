package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class LegacySettingsTest {
    @Test fun rejectsDirectoriesAndMissingPaths() {
        val directory = Files.createTempDirectory("legacy-settings-directory")
        try {
            assertFailsWith<IllegalArgumentException> { readLegacySettings(directory) }
            assertFailsWith<IllegalArgumentException> { readLegacySettings(directory.resolve("missing.cfg")) }
        } finally { Files.delete(directory) }
    }
    @Test fun importsRepositoryTemplateAndDoesNotWriteSource() {
        val path = listOf(Path.of("../ui_layout.cfg"), Path.of("ui_layout.cfg")).first { Files.exists(it) }
        val before = Files.readAllBytes(path)
        val imported = readLegacySettings(path)
        assertEquals(80, imported.intervalMs)
        assertEquals(true, imported.hudEnabled)
        assertEquals(true, imported.voiceEnabled)
        assertEquals(100, imported.voiceVolume)
        assertEquals(true, imported.hudAttitude)
        assertEquals(false, imported.hudAutoHideOnFocusLoss)
        assertEquals(true, imported.hudFieldChoices["sideslip"])
        assertEquals(true, imported.hudFieldChoices["roll_rate"])
        assertEquals(true, imported.hudFieldChoices["radio_altitude_estimate"])
        assertEquals(true, imported.hudFieldChoices["heading"])
        assertEquals(true, imported.hudFieldChoices["wing_sweep"])
        assertEquals(true, imported.hudFieldChoices["power"])
        assertEquals(true, imported.hudFieldChoices["thrust_power"])
        assertEquals(true, imported.hudFieldChoices["endurance_clock"])
        assertEquals(true, imported.hudFieldChoices["mass_estimate"])
        assertEquals(true, imported.hudFieldChoices["engine_temperature"])
        assertEquals(true, imported.hudFieldChoices["oil_temperature"])
        assertEquals(true, imported.hudFieldChoices["engine1_manifold_auto"])
        assertEquals(true, imported.hudFieldChoices["fuel"])
        assertFalse("thrust" in imported.hudFieldChoices)
        assertEquals(true, imported.hudFieldChoices["engine1_thrust"])
        assertEquals(true, imported.hudFieldChoices["engine1_rpm"])
        assertEquals(true, imported.hudFieldChoices["engine1_pitch"])
        assertContentEquals(before, Files.readAllBytes(path))
    }

    @Test fun rejectsInvalidUtf8() {
        val path = Files.createTempFile("legacy-settings", ".cfg")
        try {
            Files.write(path, byteArrayOf(0xC3.toByte(), 0x28))
            assertFails { readLegacySettings(path) }
        } finally { Files.deleteIfExists(path) }
    }
}
