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

class HudScenePresetsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedPresetCanBeLoadedDirectlyFromVerticalMode() {
        val preset = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
        var settings by mutableStateOf(AppSettings(hudScenePresets = mapOf("战斗" to preset), hudEnabled = false,
            hudClickThrough = false, hudFontScale = 1.5f))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp)) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-name").performTextReplacement("新布局")
        compose.onNodeWithTag("hud-preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("hud-preset-load-战斗").performClick()
        compose.runOnIdle {
            assertEquals(preset, settings.hudSceneLayout)
            assertFalse(settings.hudEnabled)
            assertFalse(settings.hudClickThrough)
            assertEquals(1.5f, settings.hudFontScale)
        }
        compose.onNodeWithTag("hud-scene-toggle").performClick()
        compose.runOnIdle { assertFalse(settings.hudSceneLayout!!.enabled) }
        compose.onNodeWithTag("hud-preset-load-战斗").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(preset, settings.hudSceneLayout) }
    }

    @Test fun saveLoadReplaceAndDeleteRetainUnrelatedPreferences() {
        val original = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 240,
            engineIndex = 2, fields = listOf("rpm", "future"), title = "右发动机", fontScale = 1.5f)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = original))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp)) {
            HudScenePresetSettings(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("hud-preset-name").performTextReplacement(" 巡航 ")
        compose.onNodeWithTag("hud-preset-save").performClick()
        compose.runOnIdle {
            assertEquals(original, settings.hudScenePresets["巡航"])
            settings = settings.copy(hudSceneLayout = original.resizeCanvas(600, 400), hudFontScale = 2f, hudOpacity = .25f)
        }
        compose.onNodeWithTag("hud-preset-load-巡航").performClick()
        compose.runOnIdle {
            assertEquals(original, settings.hudSceneLayout)
            assertEquals(2f, settings.hudFontScale)
            assertEquals(.25f, settings.hudOpacity)
            settings = settings.copy(hudSceneLayout = original.copy(enabled = false))
        }
        compose.onNodeWithText("替换同名布局").assertIsDisplayed()
        compose.onNodeWithTag("hud-preset-save").performClick()
        compose.runOnIdle {
            assertFalse(settings.hudScenePresets.getValue("巡航").enabled)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        compose.onNodeWithTag("hud-preset-load-巡航").performClick()
        compose.runOnIdle { assertTrue(settings.hudSceneLayout!!.enabled) }
        compose.onNodeWithTag("hud-preset-delete-巡航").performClick()
        compose.runOnIdle { assertTrue(settings.hudScenePresets.isEmpty()); assertEquals(original, settings.hudSceneLayout) }
    }
}
