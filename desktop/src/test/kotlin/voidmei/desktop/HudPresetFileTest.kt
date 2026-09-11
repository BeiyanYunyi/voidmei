package voidmei.desktop

import org.junit.Test
import java.nio.file.Files
import voidmei.config.*
import kotlin.test.*

class HudPresetFileTest {
    @Test fun fileRoundTripAndFailedOverwritePreserveOriginalBytes() {
        val root = Files.createTempDirectory("voidmei-presets-")
        try {
            val path = root.resolve("backup.json")
            val scene = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
            val presets = mapOf("巡航" to scene)
            writeHudPresets(path, presets)
            val original = Files.readAllBytes(path)
            assertEquals(presets, readHudPresets(path))
            assertFails { writeHudPresets(path, emptyMap()) }
            assertContentEquals(original, Files.readAllBytes(path))
            Files.list(root).use { assertEquals(1L, it.count()) }
            Files.write(path, byteArrayOf(0xc3.toByte(), 0x28))
            assertFails { readHudPresets(path) }
            Files.write(path, ByteArray(HudPresetFile.MAX_BYTES + 1) { 32 })
            assertFails { readHudPresets(path) }
            assertFails { readHudPresets(root) }
        } finally { root.toFile().deleteRecursively() }
    }
}
