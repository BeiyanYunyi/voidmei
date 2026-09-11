package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.*

class HudScrollIndicatorGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overflowShowsPositionAndClearsWhenContentFits() {
        val telemetry = TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!
        var connection by mutableStateOf<ConnectionState>(ConnectionState.Flying(telemetry, FlightMetrics()))
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(440.dp, 260.dp)) {
                HudPanel(connection, AppSettings(hudFields = HudField.entries.map { it.id }), emptyList(), null) { Text("HUD") }
            }
        } }
        val indicator = compose.onNodeWithTag("hud-scroll-indicator")
        fun progress() = indicator.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(0f, progress())
        val before = indicator.captureToImage().toPixelMap()
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo().assertIsDisplayed()
        assertTrue(progress() > .5f)
        val after = indicator.captureToImage().toPixelMap()
        assertTrue(before[before.width - 2, 6].alpha > after[after.width - 2, 6].alpha)
        compose.runOnIdle { connection = ConnectionState.WaitingForFlight }
        indicator.assertDoesNotExist()
        compose.onNodeWithText("已连接 · 等待飞行").assertIsDisplayed()
    }
}
