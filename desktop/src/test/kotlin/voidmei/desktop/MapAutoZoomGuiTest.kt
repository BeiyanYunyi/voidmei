package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*
import kotlin.test.*

class MapAutoZoomGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clusteredMarkersZoomAndRemainClickableAfterMoving() {
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(10000.0, 10000.0), 1, null, null)
        fun point(x: Double, y: Double) = MapObject("aircraft", null, 0xFF0000, MapPoint(x, y), null, null, null, null)
        var snapshot by mutableStateOf(MapSnapshot(bounds, listOf(point(.1, .1), point(.3, .3))))
        compose.setContent { MaterialTheme { Column { MapObjectPlot(snapshot) } } }
        fun checkAndClick() {
            val plot = compose.onNodeWithTag("map-objects-plot")
            val pixels = plot.captureToImage().toPixelMap()
            val color = pixels[(pixels.width * .08).toInt(), (pixels.height * .08).toInt()]
            assertTrue(color.red > .9f && color.green < .1f)
            plot.performTouchInput { click(Offset(width * .08f, height * .08f)) }
            compose.onNodeWithText("点击时对象：aircraft · 图标未知").assertExists()
            compose.onNodeWithText("清除对象选择").performClick()
        }
        checkAndClick()
        compose.runOnIdle { snapshot = snapshot.copy(objects = listOf(point(.6, .6), point(.8, .8))) }
        checkAndClick()
        compose.onNodeWithText("距离标尺：500 m").assertExists()
    }
}
