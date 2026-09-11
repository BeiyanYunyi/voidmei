package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*

class ReadingColorsGuiTest {
    @get:Rule val compose = createComposeRule()
    private fun color(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }
    @Test fun tablePaletteUpdatesMainAndInheritedHudButPreservesDedicatedHudOverride() {
        var settings by mutableStateOf(AppSettings(readingColors = mapOf("label" to "#00FF00", "value" to "#0000FF"),
            hudFields = listOf("ias"), hudAttitude = false, hudMechanization = false))
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!
        compose.setContent { MaterialTheme { Column {
            CompositionLocalProvider(LocalReadingColors provides readingColors(settings)) {
                FlightReadings(listOf("主表速度" to "380 km/h"), compact = false)
                HudEnginePanel(listOf(Engine(1, null, 2500.0, null, null, null, null)), 1, compact = false)
            }
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) {}
        } } }
        assertEquals(Color.Green, color("主表速度"))
        assertEquals(Color.Blue, color("380 km/h"))
        assertEquals(Color.Blue, color("2500 RPM"))
        assertEquals(Color.Blue, color("450 km/h"))
        compose.runOnIdle { settings = settings.copy(hudValueColor = "#FF0000", readingColors = settings.readingColors + ("value" to "#00FF00")) }
        assertEquals(Color.Green, color("380 km/h"))
        assertEquals(Color.Red, color("450 km/h"))
        compose.runOnIdle { settings = settings.copy(hudValueColor = null) }
        assertEquals(Color.Green, color("450 km/h"))
    }
}
