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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.HudMessageState
import kotlin.test.*

class HudRegionVisibilityGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsHideAndRestoreReadingsWithoutLosingRegionConfiguration() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 20, 30, 300, 200,
            .25f, .75f, 2, listOf("rpm"))
        val initial = AppSettings(hudSceneLayout = HudSceneLayout(400, 300, listOf(region)))
        var settings by mutableStateOf(initial)
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(550.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(400.dp, 300.dp)) {
                HudPanel(hudPreviewFlight(), settings, emptyList(), null) {}
                HudSceneDragOverlay(settings.hudSceneLayout!!, { _, _, _ -> })
            }
        } } }
        compose.onNodeWithText("2300 RPM").assertIsDisplayed()
        compose.onNodeWithText("调整分区位置与透明度").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-visible-engine").performScrollTo().performClick()
        compose.onNodeWithText("2300 RPM").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-engine").assertDoesNotExist()
        compose.onNodeWithText("发动机 · 已隐藏").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(region.copy(visible = false), settings.hudSceneLayout!!.regions.single())
            settings = SettingsJson.decode(SettingsJson.encode(settings))
        }
        compose.onNodeWithTag("hud-region-visible-engine").performScrollTo().performClick()
        compose.onNodeWithText("2300 RPM").assertIsDisplayed()
        compose.onNodeWithText("发动机 · 已隐藏").assertDoesNotExist()
        compose.runOnIdle { assertEquals(initial, settings) }
    }

    @Test fun hidingAllMessageRegionsStopsReaderUnlessSettingsPanelNeedsIt() {
        val region = HudRegion("messages", HudRegionContent.MESSAGES, 0, 0, 300, 200)
        val scene = HudSceneLayout(400, 300, listOf(region, region.copy(id = "second")))
        var settings by mutableStateOf(AppSettings(hudEnabled = true, hudSceneLayout = scene))
        var panelExpanded by mutableStateOf(false)
        var active = 0
        var starts = 0
        compose.setContent {
            rememberHudMessageSession("http://test", hudPreviewFlight(), settings.needsHudMessages() || panelExpanded, 0) {
                flow {
                    active++; starts++
                    try { emit(HudMessageState(emptyList())); awaitCancellation() }
                    finally { active-- }
                }
            }
        }
        compose.waitUntil(5000) { active == 1 }
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene.copy(regions = listOf(region.copy(visible = false), scene.regions[1]))) }
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, active) }
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene.copy(regions = scene.regions.map { it.copy(visible = false) })) }
        compose.waitUntil(5000) { active == 0 }
        compose.runOnIdle { panelExpanded = true }
        compose.waitUntil(5000) { active == 1 }
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene) }
        compose.runOnIdle { assertEquals(2, starts); panelExpanded = false }
        compose.runOnIdle { assertEquals(1, active); settings = settings.copy(hudEnabled = false) }
        compose.waitUntil(5000) { active == 0 }
        compose.runOnIdle { settings = settings.copy(hudEnabled = true, hudSceneLayout = scene.copy(enabled = false)) }
        compose.runOnIdle { assertEquals(0, active); assertEquals(2, starts) }
    }
}
