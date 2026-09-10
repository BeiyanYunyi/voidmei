package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class ImageCrosshairGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun samePathChangesDisappearAndRecoverWithoutReselection() {
        val file = Files.createTempFile("crosshair-live-", ".png")
        fun write(rgb: Int, timestamp: Long) {
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            for (y in 0..7) for (x in 0..7) image.setRGB(x, y, rgb)
            ImageIO.write(image, "png", file.toFile())
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(timestamp))
        }
        fun waitForColor(green: Boolean) = compose.waitUntil(5000) {
            if (compose.onAllNodesWithTag("hud-image-crosshair").fetchSemanticsNodes().isEmpty()) false else {
                val pixels = compose.onNodeWithTag("hud-image-crosshair").captureToImage().toPixelMap()
                val c = pixels[pixels.width / 2, pixels.height / 2]
                if (green) c.green > .9f && c.red < .1f else c.red > .9f && c.green < .1f
            }
        }
        try {
            write(0xFF00FF00.toInt(), 1000)
            compose.setContent { MaterialTheme { ImageCrosshair(file.toString(), 80, Modifier.size(100.dp)) } }
            waitForColor(true)
            write(0xFFFF0000.toInt(), 2000)
            waitForColor(false)
            Files.delete(file)
            compose.waitUntil(5000) { compose.onAllNodesWithText("准星图片不可用，请重新选择图片。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("hud-image-crosshair").assertDoesNotExist()
            write(0xFF00FF00.toInt(), 3000)
            waitForColor(true)
            compose.onNodeWithText("准星图片不可用，请重新选择图片。").assertDoesNotExist()
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun transparentImageRendersAndBadReplacementClearsOldImage() {
        val image = Files.createTempFile("crosshair-", ".png")
        val bad = Files.createTempFile("crosshair-", ".png")
        try {
            val pixels = BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB)
            for (y in 4..11) for (x in 8..23) pixels.setRGB(x, y, 0xFF00FF00.toInt())
            ImageIO.write(pixels, "png", image.toFile())
            Files.writeString(bad, "not an image")
            var path by mutableStateOf(image.toString())
            var stretch by mutableStateOf(false)
            var right by mutableStateOf(false)
            compose.setContent { MaterialTheme { ImageCrosshair(path, 160, Modifier.size(200.dp), stretch, right) } }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-image-crosshair").fetchSemanticsNodes().isNotEmpty() }
            val centered = compose.onNodeWithTag("hud-image-crosshair").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { right = true }
            val aligned = compose.onNodeWithTag("hud-image-crosshair").fetchSemanticsNode().boundsInRoot
            assertEquals(centered.width / 8, aligned.left - centered.left, 1f)
            assertEquals(centered.top, aligned.top, 1f)
            val bitmap = compose.onNodeWithTag("hud-image-crosshair").captureToImage().toPixelMap()
            val center = bitmap[bitmap.width / 2, bitmap.height / 2]
            assertTrue(center.green > .9f && center.red < .1f)
            assertTrue(bitmap[0, 0].green < .9f || bitmap[0, 0].red > .1f || bitmap[0, 0].alpha < .1f)
            val fitPoint = bitmap[bitmap.width / 2, (bitmap.height * .3).toInt()]
            assertFalse(fitPoint.green > .9f && fitPoint.red < .1f)
            compose.runOnIdle { stretch = true }
            val stretched = compose.onNodeWithTag("hud-image-crosshair").captureToImage().toPixelMap()
            val stretchPoint = stretched[stretched.width / 2, (stretched.height * .3).toInt()]
            assertTrue(stretchPoint.green > .9f && stretchPoint.red < .1f)
            compose.runOnIdle { path = bad.toString() }
            compose.waitUntil(5000) { compose.onAllNodesWithText("准星图片不可用，请重新选择图片。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("hud-image-crosshair").assertDoesNotExist()
            compose.runOnIdle { path = image.toString() }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-image-crosshair").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("准星图片不可用，请重新选择图片。").assertDoesNotExist()
        } finally { Files.deleteIfExists(image); Files.deleteIfExists(bad) }
    }
}
