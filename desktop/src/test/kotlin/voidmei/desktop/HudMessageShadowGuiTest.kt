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
import kotlin.test.*
import voidmei.config.*
import voidmei.telemetry.*

class HudMessageShadowGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun messagesAndAlertsDrawConfiguredShadowsAndRemoveThemImmediately() {
        var settings by mutableStateOf(AppSettings(hudShadeColor = "#00FF00", hudFontScale = 1.5f,
            hudSceneLayout = HudSceneLayout(500, 400, listOf(
                HudRegion("messages", HudRegionContent.MESSAGES, 0, 0, 500, 200),
                HudRegion("alerts", HudRegionContent.ALERTS, 0, 200, 500, 200)))))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 400.dp)) {
            HudPanel(hudPreviewFlight(), settings, listOf(FlightAlert.EMPTY_FUEL), null,
                messages = HudMessageState(listOf(HudMessage(HudMessageKind.EVENT, 1, "消息阴影")))) {}
        } } }
        fun green(node: SemanticsNodeInteraction): Int {
            val pixels = node.captureToImage().toPixelMap()
            return (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x ->
                val color = pixels[x, y]
                color.green > .5f && color.red < .3f && color.blue < .3f
            } }
        }
        val nodes = listOf(compose.onNodeWithText("事件 #1 · 消息阴影"),
            compose.onNodeWithTag("flight-alert-EMPTY_FUEL"))
        nodes.forEach { assertTrue(green(it) > 10) }
        compose.runOnIdle { settings = settings.copy(hudShadeColor = null) }
        nodes.forEach { it.assertIsDisplayed(); assertEquals(0, green(it)) }
    }
}
