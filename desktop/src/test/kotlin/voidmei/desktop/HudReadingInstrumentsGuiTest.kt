package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import voidmei.config.*
import voidmei.telemetry.*
import voidmei.fm.*

class HudReadingInstrumentsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun engineGraphicsTogglePreservesNumbersAndOtherRegions() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 400, fields = listOf("throttle"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 400,
            listOf(one, one.copy(id = "two", x = 400)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(450.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(800.dp, 400.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        compose.onAllNodesWithTag("hud-engine-throttle-1").assertCountEquals(2)
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-engine-instruments-one").performScrollTo().performClick()
        compose.onAllNodesWithText("95 %").assertCountEquals(2)
        compose.onAllNodesWithTag("hud-engine-throttle-1").assertCountEquals(1)
        compose.onNode(hasTestTag("hud-engine-throttle-1") and hasAnyAncestor(hasTestTag("hud-region-two"))).assertIsDisplayed()
        compose.runOnIdle { assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings))) }
        compose.onNodeWithTag("hud-region-engine-instruments-one").performClick()
        compose.onAllNodesWithTag("hud-engine-throttle-1").assertCountEquals(2)
    }

    @Test fun hidingInstrumentsRetainsNumericAoaWarning() {
        val flight = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(angleOfAttackDeg = 30.0)) }
        val model = AircraftAlertModel("preview", FlightModelParameters(null, null,
            listOf(WingConfiguration(0.0, 500.0, .9, -10.0, 15.0, -8.0, 18.0)), false, emptyList()))
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 200.dp)) {
            FlightPanel(flight, compact = true, fields = listOf(HudField.AOA), mechanization = false,
                model = model, showInstruments = false)
        } } }
        compose.onNodeWithText("30.0 °").assertIsDisplayed().assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "模型迎角余量预警"))
    }

    @Test fun numericRegionCanHideItsInstrumentsWithoutAffectingOtherRegions() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 400, fields = listOf("heading", "engine1_throttle"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 400, listOf(one, one.copy(id = "two", x = 400)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(450.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(800.dp, 400.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        compose.onAllNodesWithTag("hud-compass").assertCountEquals(2)
        compose.onAllNodesWithTag("hud-throttle-bar").assertCountEquals(2)
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-instruments-one").performScrollTo().performClick()
        compose.onAllNodesWithText("95 %").assertCountEquals(2)
        compose.onAllNodesWithTag("hud-compass").assertCountEquals(1)
        compose.onAllNodesWithTag("hud-throttle-bar").assertCountEquals(1)
        compose.onNode(hasTestTag("hud-compass") and hasAnyAncestor(hasTestTag("hud-region-two"))).assertIsDisplayed()
        compose.runOnIdle { assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings))) }
        compose.onNodeWithTag("hud-region-instruments-one").performClick()
        compose.onAllNodesWithTag("hud-compass").assertCountEquals(2)
        compose.onAllNodesWithTag("hud-throttle-bar").assertCountEquals(2)
    }
}
