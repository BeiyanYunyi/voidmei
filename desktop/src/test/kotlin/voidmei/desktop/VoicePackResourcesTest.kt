package voidmei.desktop

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.sound.sampled.*
import kotlin.test.*
import voidmei.telemetry.FlightAlert

class VoicePackResourcesTest {
    private fun wav(sample: Byte): ByteArray {
        val format = AudioFormat(8000f, 8, 1, false, false)
        val output = ByteArrayOutputStream()
        AudioInputStream(ByteArrayInputStream(ByteArray(800) { sample }), format, 800).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, output)
        }
        return output.toByteArray()
    }

    @Test fun selectedPackThenRootThenBundledFallback() {
        val root = Files.createTempDirectory("voice-pack")
        val pack = Files.createDirectory(root.resolve("中文包"))
        val alert = FlightAlert.entries.first()
        val filename = "${alert.voice}.wav"
        try {
            val resources = VoiceResources(root, "中文包")
            resources.open(alert).use { assertTrue(it.frameLength > 0) }
            Files.write(root.resolve(filename), wav(21))
            resources.open(alert).use { assertEquals(21, it.read()) }
            Files.write(pack.resolve(filename), wav(42))
            resources.open(alert).use { assertEquals(42, it.read()) }
            VoiceResources(root).open(alert).use { assertEquals(21, it.read()) }
            Files.write(pack.resolve(filename), byteArrayOf(1, 2, 3))
            assertFails { resources.open(alert).close() }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun rejectsTraversalOversizeAndEscapingSymlinks() {
        val root = Files.createTempDirectory("voice-pack-bounds")
        val outside = Files.createTempFile("voice-outside", ".wav")
        val filename = "${FlightAlert.entries.first().voice}.wav"
        try {
            for (name in listOf("../escape", "/tmp", "..", "a\\b", "C:drive")) assertFails { VoiceResources(root, name) }
            Files.write(root.resolve(filename), ByteArray(VoiceResources.MAX_BYTES + 1))
            assertFails { VoiceResources(root).open(FlightAlert.entries.first()).close() }
            Files.delete(root.resolve(filename))
            Files.write(outside, wav(10))
            // Windows CI may lack symlink privileges; other checks still run there.
            if (!System.getProperty("os.name").startsWith("Windows")) {
                Files.createSymbolicLink(root.resolve(filename), outside)
                assertFails { VoiceResources(root).open(FlightAlert.entries.first()).close() }
            }
        } finally { root.toFile().deleteRecursively(); Files.deleteIfExists(outside) }
    }
}
