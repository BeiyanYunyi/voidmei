package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voidmei.config.*
import voidmei.telemetry.FlightAlert
import kotlin.test.*

class LegacyVoicesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val alert = FlightAlert.CONNECTION_READY

    private fun layout(root: Path): Path = root.resolve("ui_layout.user.cfg").also {
        Files.writeString(it, """(panel p (item v :type voice :target voice_${alert.voice} :value "custom|false"))""")
    }

    @Test fun importedPackResolvesItsOriginalAudioAndPersistsWithoutEnablingMutedAlerts() {
        val root = temporary.root.toPath()
        val source = layout(root)
        val directory = Files.createDirectories(root.resolve("voice/custom"))
        val audioFile = directory.resolve("${alert.voice}.wav")
        val samples = ByteArray(800) { (it % 100).toByte() }
        val format = AudioFormat(8000f, 8, 1, false, false)
        AudioInputStream(samples.inputStream(), format, samples.size.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, audioFile.toFile())
        }
        val before = Files.readAllBytes(audioFile)
        val imported = readLegacySettings(source)
        assertTrue(imported.unmigrated.isEmpty())
        val current = AppSettings(voiceDirectory = "/current", voicePack = "another", hudEnabled = true)
        val restored = imported.applyTo(current)
        assertEquals(current.copy(voiceDirectory = root.resolve("voice").toString(),
            alertVoices = mapOf(alert.voice to VoiceChoice(false, "custom"))), restored)
        assertEquals(restored, SettingsJson.decode(SettingsJson.encode(restored)))
        VoiceResources(Path.of(restored.voiceDirectory), restored.alertVoices.getValue(alert.voice).pack!!).open(alert).use {
            assertContentEquals(samples, it.readAllBytes())
        }
        assertContentEquals(before, Files.readAllBytes(audioFile))
    }

    @Test fun explicitResourceRootAndMissingResourcesHaveReviewableResults() {
        val source = layout(temporary.root.toPath())
        val initial = readLegacySettings(source)
        assertNull(initial.voiceDirectory)
        assertTrue(initial.unmigrated.any { it.target == "voiceDirectory" })
        assertEquals("/current", initial.applyTo(AppSettings(voiceDirectory = "/current")).voiceDirectory)
        val other = temporary.newFolder("old-app").toPath()
        Files.createDirectory(other.resolve("voice"))
        val imported = readLegacySettings(source, other)
        assertEquals(other.resolve("voice").toString(), imported.voiceDirectory)
        assertTrue(imported.unmigrated.any { it.target == "voice_${alert.voice}" && it.label.contains("默认语音回退") })
        Files.writeString(source, """(panel p (item v :type switch :target enableVoiceWarn :value false))""")
        assertNull(readLegacySettings(source, other).voiceDirectory)
    }
}
