@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*

class HudNumberFontGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unavailableFontIsReportedAndEditingRecovers() {
        var settings by mutableStateOf(AppSettings(hudNumberFont = "Voidmei Missing Font 785cab69"))
        compose.setContent { MaterialTheme {
            Column(Modifier.size(700.dp).verticalScroll(rememberScrollState())) {
                HudSettingsPanel(settings) { settings = it }
            }
        } }
        compose.onNodeWithText("HUD 字段设置").performClick()
        compose.onNodeWithTag("hud-number-font-unavailable").assertExists()
        compose.onNodeWithTag("hud-number-font").performScrollTo().performTextReplacement("monospace")
        compose.onNodeWithTag("hud-number-font-unavailable").assertDoesNotExist()
        assertEquals("monospace", settings.hudNumberFont)
        compose.onNodeWithTag("hud-number-font").performTextClearance()
        assertNull(settings.hudNumberFont)
    }

    @Test fun hudFontUpdatesReadingsAndRestoresDefaultWithoutChangingLabels() {
        var settings by mutableStateOf(AppSettings(hudNumberFont = "serif", hudFields = listOf("ias"),
            hudAttitude = false, hudMechanization = false))
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) {}
        } } }
        fun family(text: String): FontFamily? {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style.fontFamily
        }
        val labelFont = family("IAS")
        assertEquals(FontFamily.Serif, family("450 km/h"))
        compose.runOnIdle { settings = settings.copy(hudNumberFont = "sans-serif") }
        assertEquals(FontFamily.SansSerif, family("450 km/h"))
        assertEquals(labelFont, family("IAS"))
        compose.runOnIdle { settings = settings.copy(hudNumberFont = "Voidmei Missing Font 785cab69") }
        assertEquals(FontFamily.Monospace, family("450 km/h"), "Unavailable font should preserve monospaced readings")
        assertEquals("Voidmei Missing Font 785cab69", settings.hudNumberFont)
        compose.runOnIdle { settings = settings.copy(hudNumberFont = null) }
        assertEquals(FontFamily.Monospace, family("450 km/h"))
    }
}
