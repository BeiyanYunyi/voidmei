package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*

class NumberFontGuiTest {
    @get:Rule val compose = createComposeRule()

    private fun family(text: String): FontFamily? {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.fontFamily
    }

    @Test fun appliesNumbersIndependentlyOfLabelsAndPreservesMissingNames() {
        var name by mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme(typography = textTypography(FontFamily.SansSerif)) { Column {
            NumberFontSettings(name) { name = it }
            CompositionLocalProvider(LocalReadingNumberFont provides resolveHudNumberFont(name).family) {
                FlightReadings(listOf("IAS" to "380 km/h"), compact = false)
                HudEnginePanel(listOf(Engine(1, null, 2500.0, null, null, null, null)), 1, compact = false)
            }
        } } }
        compose.onNodeWithText("全局数字字体").performTextReplacement("serif")
        assertEquals(FontFamily.Monospace, family("380 km/h"))
        compose.onNodeWithText("应用数字字体").performClick()
        assertEquals(FontFamily.Serif, family("380 km/h"))
        assertEquals(FontFamily.Serif, family("2500 RPM"))
        assertEquals(FontFamily.SansSerif, family("IAS"))
        compose.onNodeWithText("全局数字字体").performTextReplacement("voidmei-missing-font-476398")
        compose.onNodeWithText("应用数字字体").performClick()
        compose.onNodeWithText("未找到数字字体", substring = true).assertExists()
        compose.runOnIdle { assertEquals("voidmei-missing-font-476398", name) }
        assertEquals(FontFamily.Monospace, family("380 km/h"))
        compose.onNodeWithText("全局数字字体").performTextReplacement("")
        compose.onNodeWithText("应用数字字体").performClick()
        compose.runOnIdle { assertNull(name) }
    }

    @Test fun hudInheritsGlobalFontUntilDedicatedFontIsSelected() {
        var settings by mutableStateOf(AppSettings(numberFont = "serif", hudFields = listOf("ias"),
            hudAttitude = false, hudMechanization = false))
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380}""", """{"valid":true,"type":"test"}""")!!
        compose.setContent { MaterialTheme {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) {}
        } }
        assertEquals(FontFamily.Serif, family("380 km/h"))
        compose.runOnIdle { settings = settings.copy(hudNumberFont = "monospace") }
        assertEquals(FontFamily.Monospace, family("380 km/h"))
        compose.runOnIdle { settings = settings.copy(numberFont = "sans-serif") }
        assertEquals(FontFamily.Monospace, family("380 km/h"))
        compose.runOnIdle { settings = settings.copy(hudNumberFont = null) }
        assertEquals(FontFamily.SansSerif, family("380 km/h"))
    }
}
