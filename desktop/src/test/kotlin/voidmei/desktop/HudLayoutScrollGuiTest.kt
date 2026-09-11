package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.*

class HudLayoutScrollGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun engineAndColumnChangesResetScrollButOrdinarySamplesDoNot() {
        var settings by mutableStateOf(AppSettings(hudFields = HudField.entries.map { it.id }, hudEngineIndex = 1))
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":300}""",
            """{"valid":true,"type":"test","aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!)
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(440.dp, 260.dp)) {
                HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) { Text("HUD") }
            }
        } }
        fun offset() = compose.onNodeWithTag("hud-body").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun bottom() {
            compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo()
            assertTrue(offset() > 0)
        }
        bottom()
        val before = offset()
        compose.runOnIdle { telemetry = telemetry.copy(iasKmh = 301.0) }
        assertEquals(before, offset())
        compose.runOnIdle { settings = settings.copy(hudEngineFields = settings.hudEngineFields.reversed()) }
        assertEquals(0f, offset())
        bottom()
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = 2) }
        assertEquals(0f, offset())
        bottom()
        compose.runOnIdle { settings = settings.copy(hudReadingColumns = 1) }
        assertEquals(0f, offset())
        bottom()
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = null) }
        assertEquals(0f, offset())
    }
}
