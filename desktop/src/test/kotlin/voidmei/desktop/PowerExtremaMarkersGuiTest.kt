package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*
import kotlin.test.*

class PowerExtremaMarkersGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun markerTipsUseDataCoordinatesAndOppositeOrientations() {
        compose.setContent {
            Canvas(Modifier.size(400.dp, 200.dp).testTag("markers")) {
                drawRect(Color.Black)
                drawPowerExtrema(listOf(PowerExtremum(PistonPowerPoint(2500.0, 1000.0, 0), PowerExtremumKind.PEAK)), 2000.0, Color.Green)
                drawPowerExtrema(listOf(PowerExtremum(PistonPowerPoint(7500.0, 1000.0, 0), PowerExtremumKind.VALLEY)), 2000.0, Color.Red)
                drawPowerExtrema(listOf(PowerExtremum(PistonPowerPoint(5000.0, 1500.0, 0), PowerExtremumKind.KINK)), 2000.0, Color.Blue)
            }
        }
        val pixels = compose.onNodeWithTag("markers").captureToImage().toPixelMap()
        val y = pixels.height / 2
        val delta = (pixels.height * 5 / 200).coerceAtLeast(1)
        val peakX = pixels.width / 4
        val valleyX = pixels.width * 3 / 4
        assertTrue(pixels[peakX, y + delta].green > 0.9f)
        assertTrue(pixels[valleyX, y - delta].red > 0.9f)
        assertEquals(Color.Black, pixels[peakX, y - delta])
        assertEquals(Color.Black, pixels[valleyX, y + delta])
        val kinkX = pixels.width / 2
        val kinkY = pixels.height / 4
        val inside = (pixels.height * 2 / 200).coerceAtLeast(1)
        assertTrue(pixels[kinkX, kinkY].blue > 0.9f)
        assertTrue(pixels[kinkX - inside, kinkY].blue > 0.9f)
        assertTrue(pixels[kinkX, kinkY + inside].blue > 0.9f)
        assertEquals(Color.Black, pixels[kinkX + delta, kinkY + delta])
    }
}
