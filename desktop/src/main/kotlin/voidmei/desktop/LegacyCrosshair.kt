package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.LegacySettings
import voidmei.config.UnmigratedLegacySetting

/** Legacy lookup assumes the chosen configuration sits in its original application directory. */
internal fun resolveLegacyCrosshair(settings: LegacySettings, source: Path, resourceRoot: Path? = null): LegacySettings {
    val pending = settings.pendingCrosshairImage ?: return settings
    val directory = (resourceRoot?.toAbsolutePath()?.normalize() ?: source.toAbsolutePath().normalize().parent).resolve("image/gunsight")
    var attempted = directory.toString()
    return try {
        require(pending.name.isNotBlank() && pending.name !in listOf(".", "..") &&
            pending.name.none { it == '/' || it == '\\' || it == '\u0000' }) { "图片名称不是单个文件名" }
        val image = directory.resolve(pending.name + ".png")
        attempted = image.toString()
        require(Files.isRegularFile(image)) { "图片文件不存在" }
        val bytes = Files.newInputStream(image).use { it.readNBytes(8 * 1024 * 1024 + 1) }
        decodeBoundedImage(bytes, "旧准星图片")
        settings.copy(hudCrosshairImage = image.toString(), hudCrosshairStretch = true, hudCrosshair = pending.enabled ?: settings.hudCrosshair,
            hudCrosshairSizeDp = pending.sizeDp ?: settings.hudCrosshairSizeDp,
            pendingCrosshairImage = null,
            unmigrated = settings.unmigrated.filterNot { it.target in setOf("crosshairName", "displayCrosshair") ||
                (it.target == "crosshairScale" && pending.sizeDp != null) })
    } catch (e: Exception) {
        settings.copy(unmigrated = settings.unmigrated + UnmigratedLegacySetting(
            "$attempted：${e.message ?: "无法加载图片"}", "crosshairName"))
    }
}
