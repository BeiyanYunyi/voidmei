package voidmei.desktop

import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import voidmei.config.AppSettings
import voidmei.config.SettingsJson
import kotlin.test.*

class LegacyCrosshairTest {
    @Test fun resolvesOnlyUsableImagesAlongsideOriginalConfiguration() {
        val root = Files.createTempDirectory("legacy-crosshair-")
        try {
            val source = root.resolve("ui_layout.user.cfg")
            fun config(name: String) = """(panel p
                (item n :target crosshairName :type combo :value "$name")
                (item e :target displayCrosshair :type switch :value true)
                (item s :target crosshairScale :type slider :value 100))"""
            Files.writeString(source, config("custom"))
            val image = Files.createDirectories(root.resolve("image/gunsight")).resolve("custom.png")
            ImageIO.write(BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB), "png", image.toFile())
            val imported = readLegacySettings(source)
            assertEquals(image.toString(), imported.hudCrosshairImage)
            assertEquals(true, imported.hudCrosshairStretch)
            assertEquals(200, imported.hudCrosshairSizeDp)
            assertEquals(true, imported.hudCrosshair)
            assertTrue(imported.unmigrated.isEmpty())
            val applied = imported.applyTo(AppSettings())
            assertEquals(applied, SettingsJson.decode(SettingsJson.encode(applied)))
            assertEquals(config("custom"), Files.readString(source))
            val separateConfig = Files.createDirectories(root.resolve("settings")).resolve("old.cfg")
            Files.copy(source, separateConfig)
            assertNull(readLegacySettings(separateConfig).hudCrosshairImage)
            assertEquals(image.toString(), readLegacySettings(separateConfig, root).hudCrosshairImage)
            val current = AppSettings(hudCrosshairImage = "/keep.png", hudCrosshair = true, hudCrosshairSizeDp = 80)
            Files.writeString(image, "broken")
            assertEquals(current, readLegacySettings(source).applyTo(current))
            Files.delete(image)
            val missing = readLegacySettings(source)
            assertEquals(current, missing.applyTo(current))
            assertTrue(missing.unmigrated.any { it.label.contains(image.toString()) })
            Files.writeString(source, config("../../escape"))
            assertEquals(current, readLegacySettings(source).applyTo(current))
        } finally { root.toFile().deleteRecursively() }
    }
}
