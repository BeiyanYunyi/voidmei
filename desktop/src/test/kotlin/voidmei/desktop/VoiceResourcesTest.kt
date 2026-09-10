package voidmei.desktop

import kotlin.test.*
import voidmei.telemetry.FlightAlert
import javax.sound.sampled.AudioSystem
import java.io.BufferedInputStream

class VoiceResourcesTest {
    @Test fun fullValidationRejectsTruncatedAudioAndPropagatesCancellation() {
        val root = java.nio.file.Files.createTempDirectory("voidmei-voice-validation")
        val alert = FlightAlert.CONNECTION_READY
        val bytes = javaClass.getResourceAsStream("/voice/${alert.voice}.wav")!!.use { it.readBytes() }
        val path = root.resolve("${alert.voice}.wav")
        try {
            val resources = VoiceResources(root)
            java.nio.file.Files.write(path, bytes)
            resources.validate(alert)
            val cancelled = kotlinx.coroutines.CancellationException("closed")
            var checks = 0
            assertSame(cancelled, assertFailsWith<kotlinx.coroutines.CancellationException> {
                resources.validate(alert) { if (++checks == 2) throw cancelled }
            })
            java.nio.file.Files.write(path, bytes.copyOf(bytes.size / 2))
            resources.open(alert).close() // Header parsing alone accepts this truncated file.
            assertTrue(assertFailsWith<IllegalArgumentException> { resources.validate(alert) }.message!!.contains("不完整"))
        } finally { java.nio.file.Files.deleteIfExists(path); java.nio.file.Files.delete(root) }
    }

    @Test fun bundledWarningsAreReadableAndWithinResourceDurationLimit() {
        for (alert in FlightAlert.entries) {
            val resource = javaClass.getResourceAsStream("/voice/${alert.voice}.wav")
            assertNotNull(resource, alert.voice)
            BufferedInputStream(resource).use { stream ->
                AudioSystem.getAudioInputStream(stream).use { audio ->
                    val seconds = audio.frameLength / audio.format.frameRate
                    assertTrue(seconds > 0 && seconds <= 30, "${alert.voice}: $seconds seconds")
                }
            }
        }
    }
}
