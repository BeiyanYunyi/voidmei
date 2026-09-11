package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*
import kotlin.test.*

class HudMapRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coordinateDigitsUseSelectedNumberFontWhileMissingPositionStaysPlain() {
        var settings by mutableStateOf(AppSettings(hudNumberFont = "serif", hudSceneLayout = HudSceneLayout(500, 500,
            listOf(HudRegion("map", HudRegionContent.MAP, 0, 0, 500, 500)))))
        val snapshot = hudPreviewMap()
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(snapshot))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
            HudPanel(hudPreviewFlight(), settings, emptyList(), null, sharedMap = map) {}
        } } }
        fun spans(text: String): List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>> {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.text.spanStyles
        }
        val text = "玩家位置 0.500, 0.500"
        var span = spans(text).single()
        assertEquals("0.500, 0.500", text.substring(span.start, span.end))
        assertEquals(androidx.compose.ui.text.font.FontFamily.Serif, span.item.fontFamily)
        compose.runOnIdle { settings = settings.copy(hudNumberFont = "monospace") }
        span = spans(text).single()
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, span.item.fontFamily)
        compose.runOnIdle { map.value = MapConnection.Available(snapshot.copy(objects = emptyList())) }
        assertTrue(spans("玩家位置未知").isEmpty())
    }

    @Test fun mapTextAndScaleUseHudColorsAndTextShadows() {
        var settings by mutableStateOf(AppSettings(hudLabelColor = "#00FF00", hudValueColor = "#0000FF", hudShadeColor = "#FF0000",
            hudSceneLayout = HudSceneLayout(500, 500, listOf(HudRegion("map", HudRegionContent.MAP, 0, 0, 500, 500)))))
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(hudPreviewMap()))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
            HudPanel(hudPreviewFlight(), settings, emptyList(), null, sharedMap = map) {}
        } } }
        fun style(text: String): androidx.compose.ui.text.TextStyle {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style
        }
        assertEquals(androidx.compose.ui.graphics.Color.Green, style("地图对象 · 3 · 无底图").color)
        assertEquals(androidx.compose.ui.graphics.Color.Blue, style("玩家位置 0.500, 0.500").color)
        assertEquals(androidx.compose.ui.graphics.Color.Red, style("玩家位置 0.500, 0.500").shadow!!.color)
        compose.runOnIdle { settings = settings.copy(hudValueColor = "#00FF00", hudShadeColor = null) }
        assertEquals(androidx.compose.ui.graphics.Color.Green, style("玩家位置 0.500, 0.500").color)
        assertNull(style("玩家位置 0.500, 0.500").shadow)
    }

    @Test fun mapUsesRemainingHeightAfterWrappedTitleAndKeepsScaleVisible() {
        val original = HudRegion("map", HudRegionContent.MAP, 0, 0, 240, 500)
        var region by mutableStateOf(original)
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(hudPreviewMap()))
        compose.setContent { MaterialTheme { Box(Modifier.size(240.dp, 500.dp)) {
            HudPanel(hudPreviewFlight(), AppSettings(hudFontScale = 2f,
                hudSceneLayout = HudSceneLayout(240, 500, listOf(region))), emptyList(), null, sharedMap = map) {}
        } } }
        val first = compose.onNodeWithTag("map-objects-plot").getUnclippedBoundsInRoot()
        compose.runOnIdle { region = region.copy(title = "战场地图与位置".repeat(8)) }
        val next = compose.onNodeWithTag("map-objects-plot").getUnclippedBoundsInRoot()
        assertTrue(next.bottom - next.top < first.bottom - first.top)
        assertTrue(next.bottom - next.top >= 60.dp)
        val bounds = compose.onNodeWithTag("hud-region-map").getUnclippedBoundsInRoot()
        for (node in listOf(compose.onNodeWithText(region.title), compose.onNodeWithText("玩家位置 0.500, 0.500"),
            compose.onNodeWithTag("map-distance-scale"))) {
            node.assertIsDisplayed()
            assertTrue(node.getUnclippedBoundsInRoot().bottom <= bounds.bottom)
        }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.runOnIdle { region = original }
        assertEquals(first, compose.onNodeWithTag("map-objects-plot").getUnclippedBoundsInRoot())
    }

    @Test fun narrowRegionKeepsPlayerAndScaleVisibleWithLargerFonts() {
        val scene = HudSceneLayout(240, 400, listOf(HudRegion("map", HudRegionContent.MAP, 0, 0, 240, 400)))
        var fontScale by mutableStateOf(1f)
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(hudPreviewMap()))
        compose.setContent { MaterialTheme { Box(Modifier.size(240.dp, 400.dp)) {
            HudPanel(hudPreviewFlight(), AppSettings(hudSceneLayout = scene, hudFontScale = fontScale),
                emptyList(), null, sharedMap = map) {}
        } } }
        for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            val region = compose.onNodeWithTag("hud-region-map").getUnclippedBoundsInRoot()
            for (node in listOf(compose.onNodeWithText("玩家位置 0.500, 0.500"),
                compose.onNodeWithTag("map-distance-scale"), compose.onNodeWithTag("map-objects-plot"))) {
                node.assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.bottom <= region.bottom, "content below region at font scale $scale")
                assertTrue(bounds.left >= region.left && bounds.right <= region.right)
            }
        }
    }

    @Test fun regionsShareMapAndClearObjectsOnErrorsDelaysAndHiding() {
        val region = HudRegion("map", HudRegionContent.MAP, 0, 0, 360, 550)
        val scene = HudSceneLayout(760, 600, listOf(region, region.copy(id = "second", x = 380)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene))
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(hudPreviewMap()))
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(760.dp, 600.dp)) {
            HudPanel(connection, settings, emptyList(), null, sharedMap = map) {}
        } } }
        compose.onAllNodesWithTag("map-objects-plot").assertCountEquals(2)
        compose.onAllNodesWithText("玩家位置 0.500, 0.500").assertCountEquals(2)
        compose.onAllNodesWithText("玩家格号：F6").assertCountEquals(2)
        compose.onAllNodesWithText("地图对象 · 3 · 无底图").assertCountEquals(2)
        compose.onAllNodesWithTag("map-objects-plot")[0].performTouchInput { click(center) }
        compose.onAllNodesWithText("点击时对象：", substring = true).assertCountEquals(0)
        compose.runOnIdle { map.value = MapConnection.Unavailable("test") }
        compose.onAllNodesWithTag("map-objects-plot").assertCountEquals(0)
        compose.onAllNodesWithText("地图不可用：test").assertCountEquals(2)
        compose.runOnIdle { map.value = MapConnection.Available(hudPreviewMap()); connection = ConnectionState.Delayed }
        compose.onAllNodesWithTag("map-objects-plot").assertCountEquals(0)
        compose.runOnIdle { connection = hudPreviewFlight(); settings = settings.copy(hudSceneLayout = scene.copy(
            regions = listOf(region.copy(visible = false), scene.regions[1]))) }
        compose.onAllNodesWithTag("map-objects-plot").assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, map.subscriptionCount.value) }
    }

    @Test fun previewUsesLocalMapAndMissingModeRemovesIt() {
        val scene = HudSceneLayout(440, 600, listOf(HudRegion("map", HudRegionContent.MAP, 0, 0, 440, 600)))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 600.dp)) {
            HudLayoutPreview(AppSettings(hudSceneLayout = scene), missing = missing)
        } } }
        compose.onNodeWithTag("map-objects-plot").assertIsDisplayed()
        compose.runOnIdle { missing = true }
        compose.onNodeWithTag("map-objects-plot").assertDoesNotExist()
        compose.onNodeWithText("等待有效飞行地图").assertIsDisplayed()
    }
}
