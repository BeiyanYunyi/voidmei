package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CancellationException
import kotlin.test.*

class VoicePackInstallerTest {
    @Test fun invalidSourceDoesNotCreateInstallationDirectory() {
        val temp = Files.createTempDirectory("voice-source")
        try {
            val root = temp.resolve("voice")
            for (source in listOf(temp, temp.resolve("missing.zip"))) {
                assertFailsWith<IllegalArgumentException> { VoicePackInstaller.install(source, root, "custom") }
                assertFalse(Files.exists(root))
            }
        } finally { temp.toFile().deleteRecursively() }
    }
    private fun zip(path: Path, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            entries.forEach { (name, bytes) -> output.putNextEntry(ZipEntry(name)); output.write(bytes); output.closeEntry() }
        }
    }
    private fun audio() = javaClass.getResourceAsStream("/voice/aoaCrit.wav")!!.use { it.readBytes() }

    @Test fun flattensAndValidatesAudioWithoutOverwritingExistingPack() {
        val temp = Files.createTempDirectory("voice-install")
        try {
            val archive = temp.resolve("pack.zip")
            val root = temp.resolve("voice")
            zip(archive, listOf("nested/aoaCrit.WAV" to audio(), "README.txt" to byteArrayOf(1)))
            val installed = VoicePackInstaller.install(archive, root, "custom")
            assertEquals(1, installed.files)
            assertContentEquals(audio(), Files.readAllBytes(installed.directory.resolve("aoaCrit.wav")))
            assertFails { VoicePackInstaller.install(archive, root, "custom") }
            assertContentEquals(audio(), Files.readAllBytes(installed.directory.resolve("aoaCrit.wav")))
        } finally { temp.toFile().deleteRecursively() }
    }

    @Test fun failedArchivesLeaveNoPartialPackOrStagingFiles() {
        val temp = Files.createTempDirectory("voice-install-fail")
        try {
            val archive = temp.resolve("pack.zip")
            val root = temp.resolve("voice")
            val cases = listOf(
                listOf("a/aoaCrit.wav" to audio(), "b/AOACRIT.wav" to audio()),
                listOf("../aoaCrit.wav" to audio()),
                listOf("good.wav" to audio(), "broken.wav" to byteArrayOf(0)),
                listOf("empty.txt" to byteArrayOf(0)),
                listOf("truncated.wav" to audio().let { it.copyOf(it.size - 20) }),
                listOf("large.wav" to ByteArray(VoiceResources.MAX_BYTES + 1)),
                (0..VoicePackInstaller.MAX_ENTRIES).map { "$it.txt" to byteArrayOf(0) },
            )
            cases.forEach { entries ->
                zip(archive, entries)
                assertFails { VoicePackInstaller.install(archive, root, "custom") }
                Files.list(root).use { assertEquals(0, it.count()) }
            }
        } finally { temp.toFile().deleteRecursively() }
    }

    @Test fun cancellationBeforePublishingCleansPreparedFiles() {
        val temp = Files.createTempDirectory("voice-install-cancel")
        try {
            val archive = temp.resolve("pack.zip")
            val root = temp.resolve("voice")
            zip(archive, listOf("aoaCrit.wav" to audio()))
            assertFailsWith<CancellationException> {
                VoicePackInstaller.install(archive, root, "custom") {
                    if (Files.exists(root)) Files.list(root).use { paths ->
                        if (paths.anyMatch { Files.exists(it.resolve("aoaCrit.wav")) }) throw CancellationException()
                    }
                }
            }
            Files.list(root).use { assertEquals(0, it.count()) }
        } finally { temp.toFile().deleteRecursively() }
    }
}
