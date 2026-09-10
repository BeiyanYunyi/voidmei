package voidmei.desktop

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import voidmei.telemetry.FlightAlert

/** Existing pack directory -> root default file -> bundled default. No implicit installation. */
class VoiceResources(private val root: Path = Path.of("voice"), private val pack: String = "default") {
    init { require(voidmei.config.isVoicePackName(pack)) { "语音包名称必须为单个目录名" } }

    fun open(alert: FlightAlert): AudioInputStream {
        val filename = "${alert.voice}.wav"
        var bytes: ByteArray? = null
        if (!Files.notExists(root, NOFOLLOW_LINKS)) {
            val base = root.toRealPath()
            require(Files.isDirectory(base)) { "语音资源路径不是目录" }
            val candidates = if (pack == "default") listOf(base.resolve(filename))
                else listOf(base.resolve(pack).resolve(filename), base.resolve(filename))
            for (candidate in candidates) {
                if (Files.notExists(candidate, NOFOLLOW_LINKS)) continue
                val actual = candidate.toRealPath()
                require(actual.startsWith(base) && Files.isRegularFile(actual)) { "语音文件不在资源目录内" }
                bytes = Files.newInputStream(actual).use { it.readNBytes(MAX_BYTES + 1) }
                break
            }
        }
        val contents = bytes ?: javaClass.getResourceAsStream("/voice/$filename")?.use { it.readNBytes(MAX_BYTES + 1) }
            ?: error("缺少语音：$filename")
        return openVoiceAudio(contents)
    }

    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}

internal fun openVoiceAudio(contents: ByteArray): AudioInputStream {
    require(contents.size <= VoiceResources.MAX_BYTES) { "语音文件超过 8 MiB" }
    val audio = AudioSystem.getAudioInputStream(ByteArrayInputStream(contents))
    try {
        require(audio.format.frameSize > 0 && audio.frameLength <= VoiceResources.MAX_BYTES / audio.format.frameSize) {
            "语音声明的音频数据超过 8 MiB"
        }
        val duration = audio.frameLength.toDouble() / audio.format.frameRate
        require(audio.frameLength > 0 && duration.isFinite() && duration > 0 && duration <= 30) { "语音需为不超过 30 秒的有效音频" }
        return audio
    } catch (e: Exception) { audio.close(); throw e }
}
