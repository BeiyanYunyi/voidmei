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

    @Test fun engineLabelsAndUnavailableReadingsUseConfiguredColorsAndShadows() {
        var settings by mutableStateOf(AppSettings(hudLabelColor = "#0000FF", hudShadeColor = "#00FF00",
            hudSceneLayout = HudSceneLayout(600, 500, listOf(
                HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 600, 500,
                    fields = listOf("heat_budget", "thrust_power"), showEngineInstruments = false)))))
        var flight by mutableStateOf(hudPreviewFlight(missing = true))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 500.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        fun check(node: SemanticsNodeInteraction, shadow: Boolean) {
            node.assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(androidx.compose.ui.graphics.Color.Blue, layouts.single().layoutInput.style.color)
            val pixels = node.captureToImage().toPixelMap()
            val green = (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x ->
                val color = pixels[x, y]
                color.green > .5f && color.red < .3f && color.blue < .3f
            } }
            if (shadow) assertTrue(green > 10) else assertEquals(0, green)
        }
        val heading = compose.onNodeWithText("发动机 #1")
        val reason = compose.onNodeWithText("推进功率：缺少有效 TAS、本发动机有效推力")
        val thermal = compose.onNodeWithTag("hud-thermal-budget-status")
        listOf(heading, reason, thermal).forEach { check(it, true) }
        compose.runOnIdle { settings = settings.copy(hudShadeColor = null) }
        listOf(heading, reason, thermal).forEach { check(it, false) }
        compose.runOnIdle {
            settings = settings.copy(hudShadeColor = "#00FF00")
            flight = flight.copy(telemetry = flight.telemetry.copy(engines = emptyList()))
        }
        check(compose.onNodeWithText("此编号无可用发动机数据"), true)
        compose.runOnIdle {
            flight = hudPreviewFlight()
            val scene = settings.hudSceneLayout!!
            settings = settings.copy(hudSceneLayout = scene.copy(regions = scene.regions.map { it.copy(fields = emptyList()) }))
        }
        check(compose.onNodeWithText("未选择发动机读数"), true)
    }

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
