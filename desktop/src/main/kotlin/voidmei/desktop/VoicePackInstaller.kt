package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import voidmei.config.isVoicePackName

internal data class InstalledVoicePack(val directory: Path, val files: Int)

/** Validate in an unpublished directory, then publish only a complete, new pack. */
internal object VoicePackInstaller {
    const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    const val MAX_ENTRIES = 256

    fun install(zipPath: Path, root: Path, pack: String, checkActive: () -> Unit = {}): InstalledVoicePack {
        require(isVoicePackName(pack) && !pack.equals("default", true)) { "请指定新的非 default 语音包目录名" }
        require(Files.isRegularFile(zipPath)) { "请选择普通 ZIP 文件" }
        require(Files.size(zipPath) <= MAX_ARCHIVE_BYTES) { "ZIP 超过 64 MiB" }
        checkActive()
        Files.createDirectories(root)
        val base = root.toRealPath()
        val target = base.resolve(pack)
        require(Files.notExists(target, NOFOLLOW_LINKS)) { "语音包已存在，请使用新的目录名" }
        val staging = Files.createTempDirectory(base, ".voice-install-")
        try {
            var count = 0
            var total = 0L
            val names = mutableSetOf<String>()
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries()
                var entriesSeen = 0
                while (entries.hasMoreElements()) {
                    checkActive()
                    require(++entriesSeen <= MAX_ENTRIES) { "ZIP 项目超过 256 个" }
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".wav", true)) continue
                    val path = entry.name.replace('\\', '/')
                    val parts = path.split('/')
                    require(!path.startsWith('/') && parts.none { it == ".." || it == "." } && ':' !in path) { "ZIP 包含无效路径" }
                    val rawName = parts.last()
                    val name = rawName.dropLast(4) + ".wav"
                    require(name.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.wav"))) { "WAV 文件名无效：$rawName" }
                    require(names.add(name.lowercase(Locale.ROOT))) { "ZIP 存在同名 WAV：$name" }
                    require(entry.size in 1..VoiceResources.MAX_BYTES.toLong()) { "WAV 文件大小无效：$name" }
                    val bytes = zip.getInputStream(entry).use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            checkActive()
                            val read = stream.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_ARCHIVE_BYTES && output.size() + read <= VoiceResources.MAX_BYTES) { "解压语音超过大小限制" }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size.toLong() == entry.size && CRC32().apply { update(bytes) }.value == entry.crc) { "ZIP 文件校验失败：$name" }
                    openVoiceAudio(bytes).use { audio ->
                        // Read the entire declared audio to detect truncated WAV data before publishing.
                        val expected = audio.frameLength * audio.format.frameSize
                        var actual = 0L
                        val buffer = ByteArray(8192 * audio.format.frameSize.coerceAtMost(16))
                        while (true) {
                            checkActive()
                            val read = audio.read(buffer)
                            if (read < 0) break
                            require(read > 0) { "音频帧大小不支持：$name" }
                            actual += read
                        }
                        require(actual == expected) { "WAV 音频数据不完整：$name" }
                    }
                    Files.write(staging.resolve(name), bytes)
                    count++
                }
            }
            require(count > 0) { "ZIP 未包含 WAV 语音" }
            checkActive()
            // No REPLACE_EXISTING: an existing installation is never intentionally overwritten.
            Files.move(staging, target)
            return InstalledVoicePack(target, count)
        } finally {
            if (Files.exists(staging)) staging.toFile().deleteRecursively()
        }
    }
}
