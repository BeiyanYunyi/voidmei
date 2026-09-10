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
import voidmei.config.AppSettings
import voidmei.telemetry.*

class HudReadingColorGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hudUsesConfiguredLabelValueAndWarningColors() {
        var alerts by mutableStateOf(emptyList<FlightAlert>())
        var settings by mutableStateOf(AppSettings(hudFields = listOf("ias"), hudAttitude = false,
            hudMechanization = false, hudLabelColor = "#00FF00", hudValueColor = "#0000FF", hudWarningColor = "#FF0000"))
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, alerts, null) {}
        } } }
        fun hasColor(label: String, channel: Int): Boolean {
            val p = compose.onNodeWithText(label).captureToImage().toPixelMap()
            return (0 until p.height).any { y -> (0 until p.width).any { x ->
                val c = p[x, y]
                val rgb = listOf(c.red, c.green, c.blue)
                rgb[channel] > .8f && rgb.filterIndexed { i, _ -> i != channel }.all { it < .2f }
            } }
        }
        assertTrue(hasColor("IAS", 1))
        assertTrue(hasColor("450 km/h", 2))
        compose.runOnIdle { alerts = listOf(FlightAlert.IAS_LIMIT) }
        assertTrue(hasColor("450 km/h", 0))
        assertFalse(hasColor("450 km/h", 2))
        compose.runOnIdle { alerts = emptyList(); settings = settings.copy(hudValueColor = "#00FF00") }
        assertTrue(hasColor("450 km/h", 1))
    }

    @Test fun unitColorKeepsMissingValuesAndWarningNumbersSeparate() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!)
        val settings = AppSettings(hudFields = listOf("ias"), hudAttitude = false, hudMechanization = false,
            hudUnitColor = "#00FF00", hudWarningColor = "#FF0000", hudValueColor = "#0000FF")
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, listOf(FlightAlert.IAS_LIMIT), null) {}
        } } }
        fun check(text: String, warning: Boolean) {
            val node = compose.onNodeWithText(text)
            val p = node.captureToImage().toPixelMap()
            var green = 0
            var number = 0
            for (y in 0 until p.height) for (x in 0 until p.width) {
                val c = p[x, y]
                if (c.green > .8f && c.red < .2f && c.blue < .2f) green++
                if (warning && c.red > .8f && c.green < .2f && c.blue < .2f ||
                    !warning && c.blue > .8f && c.green < .2f && c.red < .2f) number++
            }
            assertTrue(green > 0, "Unit remains green")
            assertTrue(number > 0, "Number or missing marker retains its own color")
        }
        check("450 km/h", true)
        compose.runOnIdle { telemetry = telemetry.copy(iasKmh = null) }
        check("— km/h", false)
    }

    @Test fun shadowFollowsFontScaleHiddenLabelsAndWarnings() {
        var settings by mutableStateOf(AppSettings(hudFields = listOf("ias"), hudAttitude = false,
            hudMechanization = false, hudShadeColor = "#FF00FF", hudWarningColor = "#FF0000"))
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, listOf(FlightAlert.IAS_LIMIT), null) {}
        } } }
        fun pinkPixels(label: String): Int {
            val p = compose.onNodeWithText(label).captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until p.height) for (x in 0 until p.width) {
                val c = p[x, y]
                if (c.red > .7f && c.blue > .7f && c.green < .2f) count++
            }
            return count
        }
        assertTrue(pinkPixels("IAS") > 0)
        val initial = pinkPixels("450 km/h")
        assertTrue(initial > 0)
        compose.runOnIdle { settings = settings.copy(hudFontScale = 2f, hudHiddenLabels = listOf("ias")) }
        compose.onNodeWithText("IAS").assertDoesNotExist()
        compose.onNodeWithContentDescription("IAS").assertExists()
        assertTrue(pinkPixels("450 km/h") > initial)
        compose.runOnIdle { settings = settings.copy(hudShadeColor = null) }
        assertEquals(0, pinkPixels("450 km/h"))
        compose.onNodeWithText("450 km/h").assertIsDisplayed()
    }

    @Test fun editorPreservesLastValidColorAndRestoresDefaults() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { HudReadingColorSettings(settings) { settings = it } } }
        compose.onNodeWithText("HUD 读数颜色").performClick()
        compose.onNodeWithText("普通读数颜色").performTextInput("#12AB3480")
        compose.runOnIdle { assertEquals("#12AB3480", settings.hudValueColor) }
        compose.onNodeWithText("普通读数颜色").performTextReplacement("#wrong")
        compose.runOnIdle { assertEquals("#12AB3480", settings.hudValueColor) }
        compose.onNodeWithText("请输入六位 RGB 或八位 RGBA 十六进制颜色；仍使用上次有效颜色。").assertExists()
        compose.onNodeWithText("文字阴影颜色").performTextInput("#00000080")
        compose.runOnIdle { assertEquals("#00000080", settings.hudShadeColor) }
        compose.onNodeWithText("恢复读数默认颜色").performClick()
        compose.runOnIdle { assertEquals(AppSettings(), settings) }
        compose.onNodeWithText("#wrong").assertDoesNotExist()
        compose.onNodeWithText("标签颜色").performTextInput("#bad")
        compose.onNodeWithText("恢复读数默认颜色").performClick()
        compose.onNodeWithText("#bad").assertDoesNotExist()
    }
}
