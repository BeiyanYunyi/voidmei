package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.LegacySettings
import voidmei.config.UnmigratedLegacySetting

/** Preserve the directory that gave the imported per-alert pack names their meaning. */
internal fun resolveLegacyVoices(settings: LegacySettings, source: Path, resourceRoot: Path? = null): LegacySettings {
    if (settings.alertVoices.isEmpty()) return settings
    val root = resourceRoot?.toAbsolutePath()?.normalize() ?: source.toAbsolutePath().normalize().parent
    val directory = root.resolve("voice")
    return try {
        require(Files.isDirectory(directory)) { "语音目录不存在或不是目录；保留当前语音目录" }
        val missing = settings.alertVoices.mapNotNull { (key, choice) ->
            val pack = choice.pack ?: "default"
            val file = if (pack == "default") directory.resolve("$key.wav") else directory.resolve(pack).resolve("$key.wav")
            if (Files.isRegularFile(file)) null else UnmigratedLegacySetting(
                "$file：文件缺失，播放时将尝试默认语音回退", "voice_$key")
        }
        settings.copy(voiceDirectory = directory.toString(), unmigrated = settings.unmigrated + missing)
    } catch (e: Exception) {
        settings.copy(unmigrated = settings.unmigrated + UnmigratedLegacySetting(
            "$directory：${e.message}", "voiceDirectory"))
    }
}
