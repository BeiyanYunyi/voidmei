package voidmei.desktop

import androidx.compose.ui.geometry.Size
import org.junit.Test
import kotlin.test.*

class CompassLayoutTest {
    @Test fun measuredLabelsFitAtNormalAndLargeFontScales() {
        for (width in listOf(208f, 408f)) for (extent in listOf(14f, 28f, 56f, 84f)) {
            val height = maxOf(140f, extent * 2 + 92f)
            val (radius, distance) = compassLabelGeometry(Size(width, height), extent, 4f)
            assertTrue(radius >= 0)
            assertTrue(distance + extent / 2 <= minOf(width, height) / 2)
            assertTrue(distance - extent / 2 >= radius + 4f)
        }
        val normal = compassLabelGeometry(Size(208f, 140f), 14f, 4f)
        val larger = compassLabelGeometry(Size(208f, 140f), 56f, 4f)
        assertTrue(larger.first < normal.first)
    }
}
