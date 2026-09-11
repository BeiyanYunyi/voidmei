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
import voidmei.config.*
import kotlin.test.*

class HudRegionFieldsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionalFieldsOverrideGlobalChangesAndEmptyDoesNotInherit() {
        val layout = HudSceneLayout(1000, 300, listOf(
            HudRegion("speed", HudRegionContent.FLIGHT, 0, 0, 300, 280, fields = listOf("ias")),
            HudRegion("global", HudRegionContent.FLIGHT, 340, 0, 300, 280),
            HudRegion("engine", HudRegionContent.ENGINE, 680, 0, 300, 280, fields = listOf("water_temperature"))))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = layout, hudFields = listOf("altitude"), hudEngineFields = listOf("rpm")))
        compose.setContent { MaterialTheme { Box(Modifier.size(1000.dp, 300.dp)) {
            HudPanel(hudPreviewFlight(), settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        compose.onNodeWithText("1500 m").assertIsDisplayed()
        compose.onNodeWithText("95.0 °C").assertIsDisplayed()
        compose.onNodeWithText("2400 RPM").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("tas")) }
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        compose.onNodeWithText("364 km/h").assertIsDisplayed()
        compose.onNodeWithText("1500 m").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = layout.copy(regions = layout.regions.map {
            if (it.id == "speed") it.copy(fields = emptyList()) else it
        })) }
        compose.onNodeWithText("340 km/h").assertDoesNotExist()
        compose.onAllNodesWithText("364 km/h").assertCountEquals(1)
    }

    @Test fun editorCopiesInheritedFieldsThenEditsOrderWithoutChangingGlobalSettings() {
        val global = AppSettings(hudFields = listOf("ias", "future_field"))
        var region by mutableStateOf(HudRegion("speed", HudRegionContent.FLIGHT, 0, 0, 300, 200))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp).verticalScroll(rememberScrollState())) {
            HudRegionFieldsSettings(region, global) { region = it }
        } } }
        compose.onNodeWithTag("hud-region-fields-speed").performClick()
        compose.onNodeWithText("编辑区域字段（2）").performClick()
        compose.onNodeWithTag("hud-region-field-search-speed").performScrollTo().performTextReplacement("altitude")
        compose.onNodeWithTag("hud-region-field-add-speed-altitude").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-field-up-speed-2").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ias", "altitude", "future_field"), region.fields) }
        compose.onNodeWithTag("hud-region-field-remove-speed-0").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("altitude", "future_field"), region.fields)
            assertEquals(listOf("ias", "future_field"), global.hudFields)
        }
        compose.onNodeWithTag("hud-region-fields-speed").performScrollTo().performClick()
        compose.runOnIdle { assertNull(region.fields) }
    }
}
