package voidmei.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Properties
import voidmei.config.LegacySettings
import voidmei.config.UnmigratedLegacySetting

/** The Java launcher reads this sidecar before loading the layout, so it takes precedence. */
internal fun resolveLegacyRenderer(settings: LegacySettings, source: Path, resourceRoot: Path? = null): LegacySettings {
    val root = resourceRoot?.toAbsolutePath()?.normalize() ?: source.toAbsolutePath().normalize().parent
    val file = root.resolve("gpu_compat.properties")
    return try {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return settings
        require(Files.isRegularFile(file)) { "不是普通文件" }
        val bytes = Files.newInputStream(file).use { it.readNBytes(65537) }
        require(bytes.size <= 65536) { "文件超过 64 KiB" }
        // Match Properties.load(InputStream), including legacy escapes and ISO-8859-1 encoding.
        val properties = Properties().apply { bytes.inputStream().use { load(it) } }
        val value = properties.getProperty("softwareRenderingEnabled", "false")
        require(value.equals("true", true) || value.equals("false", true)) { "softwareRenderingEnabled 不是布尔值" }
        settings.copy(softwareRendering = value.equals("true", true), softwareRenderingSource = file.toString())
    } catch (e: Exception) {
        settings.copy(softwareRendering = null, softwareRenderingSource = null,
            unmigrated = settings.unmigrated + UnmigratedLegacySetting(
                "$file：${e.message}；保留当前软件渲染选择", "softwareRenderingEnabled"))
    }
}
