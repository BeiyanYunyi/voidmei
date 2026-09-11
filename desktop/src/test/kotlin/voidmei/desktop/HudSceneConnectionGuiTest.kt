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
import voidmei.telemetry.*

class HudSceneConnectionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyRegionReplacesOldReadingsDuringConnectionTransitions() {
        val regions = HudRegionContent.entries.mapIndexed { index, content ->
            HudRegion(content.name, content, index % 5 * 200, index / 5 * 300, 200, 300,
                fields = when (content) {
                    HudRegionContent.FLIGHT -> listOf("ias")
                    HudRegionContent.ENGINE -> listOf("rpm")
                    else -> null
                })
        }
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, regions))
        var connection by mutableStateOf<ConnectionState>(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(1000.dp, 600.dp)) {
            HudPanel(connection, settings, listOf(FlightAlert.entries.first()), null) {}
        } } }
        val liveTags = listOf("attitude-canvas", "hud-compass", "hud-crosshair", "hud-control-elevator")
        fun assertLive() {
            liveTags.forEach { compose.onNodeWithTag(it).assertExists() }
            compose.onNodeWithText("升降舵 20.0%").assertExists()
        }
        assertLive()
        for (state in listOf(ConnectionState.Delayed, ConnectionState.Disconnected("test outage"),
                ConnectionState.Connecting, ConnectionState.WaitingForFlight)) {
            compose.runOnIdle { connection = state }
            compose.onAllNodesWithText(statusText(state)).assertCountEquals(regions.size)
            regions.forEach { compose.onNodeWithTag("hud-region-${it.id}").assertExists() }
            liveTags.forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
            compose.onNodeWithText("升降舵 20.0%").assertDoesNotExist()
            compose.runOnIdle { connection = hudPreviewFlight() }
            assertLive()
        }
        compose.runOnIdle { connection = hudPreviewFlight(missing = true) }
        compose.onNodeWithText("航向未知").assertExists()
        compose.onNodeWithText("姿态数据不可用").assertExists()
        compose.onNodeWithText("升降舵 —%").assertExists()
        compose.onNodeWithText("升降舵 0.0%").assertDoesNotExist()
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
        compose.onNodeWithTag("hud-compass").assertDoesNotExist()
    }
}
