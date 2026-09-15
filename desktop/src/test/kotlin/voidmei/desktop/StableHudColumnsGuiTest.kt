package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*

class StableHudColumnsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun globalTextFontChangeRemeasuresColumnsWithoutChangingHudNumberFont() {
        val base = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380,"Mfuel, kg":1}""",
            """{"valid":true,"type":"first"}""")!!
        var telemetry by mutableStateOf(base)
        var font by mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme(typography = textTypography(resolveTextFont(font).family)) {
                Box(Modifier.size(300.dp, 500.dp)) {
                    HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                        AppSettings(textFont = font, hudNumberFont = "monospace", hudFields = listOf("ias", "fuel"),
                            hudAttitude = false, hudMechanization = false), emptyList(), null) {}
                }
            }
        }
        fun sameRow() = compose.onNodeWithText("IAS").fetchSemanticsNode().boundsInRoot.top ==
            compose.onNodeWithText("燃油").fetchSemanticsNode().boundsInRoot.top
        fun speedWidth() = compose.onNodeWithText("380 km/h").fetchSemanticsNode().boundsInRoot.width
        assertTrue(sameRow())
        val originalWidth = speedWidth()
        compose.runOnIdle { telemetry = base.copy(fuelKg = 1e20) }
        assertFalse(sameRow())
        compose.runOnIdle { telemetry = base }
        assertFalse(sameRow())
        compose.runOnIdle { font = "serif" }
        assertTrue(sameRow(), "Explicit text font changes must discard stale width measurements")
        assertEquals(originalWidth, speedWidth(), "Dedicated numeric typography must remain independent")
    }

    @Test fun flightBoundaryAndAircraftChangeResetRememberedWidths() {
        val base = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380,"Mfuel, kg":1}""",
            """{"valid":true,"type":"first"}""")!!
        var telemetry by mutableStateOf(base)
        var flying by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp, 500.dp)) {
            HudPanel(if (flying) ConnectionState.Flying(telemetry, FlightMetrics()) else ConnectionState.WaitingForFlight,
                AppSettings(hudFields = listOf("ias", "fuel"), hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } } }
        fun sameRow() = compose.onNodeWithText("IAS").fetchSemanticsNode().boundsInRoot.top ==
            compose.onNodeWithText("燃油").fetchSemanticsNode().boundsInRoot.top
        assertTrue(sameRow())
        compose.runOnIdle { telemetry = base.copy(fuelKg = 1e20) }
        assertFalse(sameRow())
        compose.runOnIdle { telemetry = base }
        assertFalse(sameRow())
        compose.runOnIdle { telemetry = base.copy(aircraft = "second") }
        assertTrue(sameRow())
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 1e20) }
        assertFalse(sameRow())
        compose.runOnIdle { flying = false }
        compose.onNodeWithText("IAS").assertDoesNotExist()
        compose.runOnIdle { flying = true; telemetry = base.copy(aircraft = "second") }
        assertTrue(sameRow())
    }

    @Test fun explicitAltitudeModeChangeResetsWidthsButAutomaticSourceChangesDoNot() {
        val base = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380,"H, m":1000}""",
            """{"valid":true,"type":"test","radio_altitude":499}""")!!
        var telemetry by mutableStateOf(base)
        var mode by mutableStateOf(HudAltitudeMode.SEA_LEVEL)
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp, 500.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics(cockpitAltitudeUnit = CockpitAltitudeUnit.METRES)),
                AppSettings(hudFields = listOf("ias", "altitude"), hudAltitudeMode = mode,
                    hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } } }
        fun sameRow() = compose.onNodeWithText("IAS").fetchSemanticsNode().boundsInRoot.top ==
            compose.onNodeWithText("高度").fetchSemanticsNode().boundsInRoot.top
        assertTrue(sameRow())
        compose.runOnIdle { mode = HudAltitudeMode.LOW_RADAR }
        assertFalse(sameRow())
        repeat(3) {
            compose.runOnIdle { telemetry = base.copy(radioAltitudeRaw = 501.0) }
            assertFalse(sameRow(), "Automatic altitude switching must retain stable columns")
            compose.runOnIdle { telemetry = base }
            assertFalse(sameRow())
        }
        compose.runOnIdle { mode = HudAltitudeMode.SEA_LEVEL }
        assertTrue(sameRow(), "Explicit altitude preference should start fresh width measurements")
    }

    @Test fun changingSelectedEngineStartsFreshLayoutMeasurements() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"power 1, hp":1e30,"power 2, hp":500}""",
            """{"valid":true,"type":"test"}""")!!)
        var index by mutableStateOf(1)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 800.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = emptyList(), hudAttitude = false, hudMechanization = false, hudEngineIndex = index), emptyList(), null) {}
        } } }
        fun sameRow() = compose.onNodeWithText("油门").fetchSemanticsNode().boundsInRoot.top ==
            compose.onNodeWithText("转速").fetchSemanticsNode().boundsInRoot.top
        assertFalse(sameRow())
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map { it.copy(powerHp = 500.0) }) }
        assertFalse(sameRow())
        compose.runOnIdle { index = 2 }
        assertTrue(sameRow())
        compose.onNodeWithText("发动机 #2").assertExists()
        compose.runOnIdle { index = 1 }
        assertTrue(sameRow())
    }

    @Test fun compactColumnsDoNotOscillateWithDigitsAndResetForNewFields() {
        var value by mutableStateOf("1 m")
        var label by mutableStateOf("高度")
        var width by mutableStateOf(300.dp)
        compose.setContent { MaterialTheme { Box(Modifier.width(width)) {
            FlightReadings(listOf("IAS" to "380 km/h", label to value), compact = true)
        } } }
        fun twoColumns() = compose.onNodeWithText("IAS").fetchSemanticsNode().boundsInRoot.top ==
            compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        assertTrue(twoColumns())
        // 留足宽度差，避免不同系统字体恰好落在双列阈值两侧。
        compose.runOnIdle { value = "12345678901234567890 m" }
        assertFalse(twoColumns())
        repeat(3) {
            compose.runOnIdle { value = "1 m" }
            assertFalse(twoColumns(), "Shorter telemetry must not switch columns back")
            compose.runOnIdle { value = "12345678901234567890 m" }
            assertFalse(twoColumns())
        }
        compose.runOnIdle { width = 600.dp }
        assertTrue(twoColumns(), "A wider window may use two columns again")
        compose.runOnIdle { width = 300.dp; label = "燃油"; value = "1 kg" }
        assertTrue(twoColumns(), "Changing fields starts fresh measurements")
    }
}
