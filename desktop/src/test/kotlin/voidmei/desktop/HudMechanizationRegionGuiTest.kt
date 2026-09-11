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
import voidmei.config.*

class HudMechanizationRegionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorSeparatesMechanicalContentAndRestoresGlobalInheritance() {
        val one = HudRegion("one", HudRegionContent.MECHANIZATION, 0, 0, 400, 240)
        var settings by mutableStateOf(AppSettings(hudGear = true, hudFlaps = false, hudAirbrake = false,
            hudFlapBar = false, hudSceneLayout = HudSceneLayout(400, 500, listOf(one, one.copy(id = "two", y = 250)))))
        val flight = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(gearPercent = 25.0, flapsPercent = 50.0)) }
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp)) {
                HudRegionFieldsSettings(settings.hudSceneLayout!!.regions.first(), settings) { value ->
                    settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(
                        regions = settings.hudSceneLayout!!.regions.map { if (it.id == value.id) value else it }))
                }
            }
            Box(Modifier.size(400.dp, 500.dp)) { HudPanel(flight, settings, emptyList(), null) {} }
        } } }
        fun reading(text: String, id: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
        reading("起落架 25.0%", "one").assertIsDisplayed()
        reading("起落架 25.0%", "two").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-fields-one").performClick()
        compose.onNodeWithTag("hud-region-mechanization-one-gear").performClick()
        reading("起落架 25.0%", "one").assertDoesNotExist()
        compose.onNodeWithText("此区域未选择机械化内容。").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-mechanization-one-flap_bar").performClick()
        reading("襟翼开度条 · 50.0%", "one").assertIsDisplayed()
        reading("起落架 25.0%", "two").assertIsDisplayed()
        compose.runOnIdle {
            settings = settings.copy(hudGear = false, hudAirbrake = true)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        reading("襟翼开度条 · 50.0%", "one").assertIsDisplayed()
        reading("起落架 25.0%", "two").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-fields-one").performClick()
        reading("襟翼开度条 · 50.0%", "one").assertDoesNotExist()
        compose.runOnIdle { assertNull(settings.hudSceneLayout!!.regions.first().fields) }
    }
}
