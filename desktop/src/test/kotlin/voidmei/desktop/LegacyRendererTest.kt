package voidmei.desktop

import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voidmei.config.AppSettings
import kotlin.test.*

class LegacyRendererTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sidecarOverridesLayoutAndExplicitResourceRootControlsLookup() {
        val layout = temporary.root.toPath().resolve("ui_layout.user.cfg")
        Files.writeString(layout, """(panel p (item r :type switch :target gpuCompatibilityMode :value false))""")
        assertEquals(false, readLegacySettings(layout).softwareRendering)
        val file = layout.resolveSibling("gpu_compat.properties")
        Files.writeString(file, "softwareRenderingEnabled=TRUE\n")
        val original = Files.readAllBytes(file)
        val imported = readLegacySettings(layout)
        assertEquals(true, imported.softwareRendering)
        assertEquals(file.toString(), imported.softwareRenderingSource)
        assertContentEquals(original, Files.readAllBytes(file))
        val root = temporary.newFolder("resources").toPath()
        Files.writeString(root.resolve("gpu_compat.properties"), "softwareRenderingEnabled=false\n")
        assertEquals(false, readLegacySettings(layout, root).softwareRendering)
        Files.writeString(file, "# legacy Properties syntax\nsoftwareRenderingEnabled:tr\\\n ue\n")
        assertEquals(true, readLegacySettings(layout).softwareRendering)
        Files.writeString(file, "# key absent: same default as the Java launcher\n")
        assertEquals(false, readLegacySettings(layout).softwareRendering)
    }

    @Test fun corruptSidecarPreservesCurrentRendererInsteadOfApplyingStaleLayout() {
        val layout = temporary.root.toPath().resolve("ui_layout.user.cfg")
        Files.writeString(layout, """(panel p (item r :type switch :target gpuCompatibilityMode :value true)
            (item v :type switch :target enableVoiceWarn :value false))""")
        val file = layout.resolveSibling("gpu_compat.properties")
        for (bytes in listOf("softwareRenderingEnabled=invalid".toByteArray(),
            "softwareRenderingEnabled=\\uZZZZ".toByteArray(), ByteArray(65537) { 32 })) {
            Files.write(file, bytes)
            val imported = readLegacySettings(layout)
            assertNull(imported.softwareRendering)
            assertTrue(imported.unmigrated.any { it.target == "softwareRenderingEnabled" })
            for (current in listOf(false, true)) {
                val restored = imported.applyTo(AppSettings(softwareRendering = current))
                assertEquals(current, restored.softwareRendering)
                assertFalse(restored.voiceEnabled)
            }
            assertContentEquals(bytes, Files.readAllBytes(file))
        }
        Files.delete(file)
        Files.createDirectory(file)
        assertNull(readLegacySettings(layout).softwareRendering)
    }
}
