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

class HudRegionRestoreGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingEvenIdenticalPresetClearsOldRemovalButSavingDoesNot() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        val two = one.copy(id = "two", x = 250, content = HudRegionContent.ENGINE)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(500, 240, listOf(one, two))))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp).verticalScroll(rememberScrollState())) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-remove-two").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-restore").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("hud-presets-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("hud-preset-name").performScrollTo().performTextReplacement("当前布局")
        compose.onNodeWithTag("hud-preset-save").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-restore").performScrollTo().assertIsDisplayed()
        // Loading the same geometry still starts a new restore context.
        compose.onNodeWithTag("hud-preset-load-当前布局").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-restore").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(one), settings.hudSceneLayout!!.regions) }
    }

    @Test fun restoreSurvivesCollapsingAndRetainsChangesMadeAfterRemoval() {
        val other = HudRegion("other", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        val removed = HudRegion("region-1", HudRegionContent.ENGINE, 500, 300, 300, 200,
            .25f, .75f, 2, listOf("rpm", "future_field"), visible = false)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 600, listOf(other, removed))))
        compose.setContent { MaterialTheme {
            Column(Modifier.size(600.dp, 600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
        } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-remove-region-1").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-add-MAP").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-editor-other").performScrollTo().performClick()
        compose.runOnIdle {
            val scene = settings.hudSceneLayout!!
            settings = settings.copy(hudOpacity = .8f, hudSceneLayout = scene.resizeCanvas(240, 120).copy(
                regions = scene.resizeCanvas(240, 120).regions.map {
                    if (it.id == "other") it.copy(backgroundAlpha = .1f) else it
                }))
        }
        val beforeRestore = settings
        compose.onNodeWithTag("hud-region-restore").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-restore").assertDoesNotExist()
        compose.runOnIdle {
            val regions = settings.hudSceneLayout!!.regions
            assertEquals(removed.copy(id = "region-2", x = 0, y = 0, width = 240, height = 120), regions[1])
            assertEquals(beforeRestore.hudSceneLayout!!.regions, listOf(regions[0], regions[2]))
            assertEquals(.8f, settings.hudOpacity)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }
}
