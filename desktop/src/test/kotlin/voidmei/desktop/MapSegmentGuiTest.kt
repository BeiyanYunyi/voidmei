package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*
import kotlin.test.assertTrue

class MapSegmentGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lineWithBothEndpointsOutsideStillDrawsItsVisibleMiddle() {
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(1000.0, 1000.0), 1, null, null)
        val line = MapObject("airfield", null, 0xFF0000, null, null,
            MapPoint(-.5, .25), MapPoint(1.5, .25), null)
        compose.setContent { MaterialTheme { Column { MapObjectPlot(MapSnapshot(bounds, listOf(line))) } } }
        val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
        for (x in listOf(pixels.width / 8, pixels.width / 2, pixels.width * 7 / 8)) {
            val color = pixels[x, pixels.height / 4]
            assertTrue(color.red > .9f && color.green < .1f && color.blue < .1f)
        }
    }
}
