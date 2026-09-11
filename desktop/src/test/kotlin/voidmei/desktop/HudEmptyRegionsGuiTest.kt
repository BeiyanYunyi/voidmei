package voidmei.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.ConnectionState

class HudEmptyRegionsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyAndUnknownSelectionsExplainBlankRegionsUntilFieldsAreSelected() {
        var scene by mutableStateOf(HudSceneLayout(800, 300, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 400, 300, fields = emptyList()),
            HudRegion("mechanization", HudRegionContent.MECHANIZATION, 400, 0, 400, 300, fields = listOf("future")),
        )))
        var connection by mutableStateOf<ConnectionState>(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudPanel(connection, AppSettings(hudSceneLayout = scene), emptyList(), null) {}
        } } }
        compose.onNodeWithText("未选择飞行读数").assertIsDisplayed()
        compose.onNodeWithText("未选择机械化内容").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.WaitingForFlight }
        compose.onNodeWithText("未选择飞行读数").assertDoesNotExist()
        compose.onNodeWithText("未选择机械化内容").assertDoesNotExist()
        compose.onAllNodesWithText(statusText(ConnectionState.WaitingForFlight)).assertCountEquals(2)
        compose.runOnIdle {
            connection = hudPreviewFlight()
            scene = scene.copy(regions = scene.regions.map {
                it.copy(fields = if (it.id == "flight") listOf("ias", "future") else listOf("gear", "future"))
            })
        }
        compose.onNodeWithText("未选择飞行读数").assertDoesNotExist()
        compose.onNodeWithText("未选择机械化内容").assertDoesNotExist()
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        compose.onNodeWithText("起落架 0.0%").assertIsDisplayed()
        compose.runOnIdle { scene = scene.copy(regions = scene.regions.map { it.copy(fields = null) }) }
        compose.onNodeWithText("未选择飞行读数").assertDoesNotExist()
        compose.onNodeWithText("未选择机械化内容").assertDoesNotExist()
    }
}
