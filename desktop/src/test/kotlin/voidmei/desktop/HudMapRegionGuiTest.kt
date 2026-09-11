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
        compose.onAllNodesWithText("地图对象示意 · 3 个对象 · 每秒更新").assertCountEquals(2)
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
