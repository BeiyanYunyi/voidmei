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

    @Test fun renamingPresetKeepsSavedContentOrderAndCurrentLayout() {
        val original = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240,
            fields = listOf("ias", "future"), showFlightInstruments = false)))
        val edited = original.resizeCanvas(600, 400)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = edited,
            hudScenePresets = mapOf("旧名称" to original, "另一套" to edited)))
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 800.dp)) {
            HudScenePresetSettings(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-rename-旧名称").assertIsNotEnabled()
        compose.onNodeWithTag("hud-preset-name").performTextReplacement("另一套")
        compose.onNodeWithTag("hud-preset-rename-旧名称").assertIsNotEnabled()
        compose.onNodeWithTag("hud-preset-name").performTextReplacement(" 新名称 ")
        compose.onNodeWithTag("hud-preset-rename-旧名称").performClick()
        compose.runOnIdle {
            assertEquals(listOf("新名称", "另一套"), settings.hudScenePresets.keys.toList())
            assertEquals(original, settings.hudScenePresets["新名称"])
            assertEquals(edited, settings.hudSceneLayout)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        compose.onNodeWithTag("hud-preset-rename-新名称").assertIsNotEnabled()
        compose.onNodeWithTag("hud-preset-load-新名称").performClick()
        compose.runOnIdle { assertEquals(original, settings.hudSceneLayout) }
    }

    @Test fun undoLoadRestoresVerticalStateAndKeepsLaterPreferencesAndPresets() {
        val first = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
        val second = first.resizeCanvas(600, 400)
        var settings by mutableStateOf(AppSettings(hudScenePresets = mapOf("一" to first, "二" to second)))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 700.dp)) {
            HudScenePresetSettings(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-load-一").performClick()
        compose.runOnIdle { settings = settings.copy(hudFontScale = 2f) }
        compose.onNodeWithTag("hud-preset-delete-二").performClick()
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-undo-load").performClick()
        compose.runOnIdle {
            assertNull(settings.hudSceneLayout)
            assertEquals(2f, settings.hudFontScale)
            assertEquals(mapOf("一" to first), settings.hudScenePresets)
            settings = settings.copy(hudSceneLayout = second.copy(enabled = false))
        }
        compose.onNodeWithTag("hud-preset-undo-load").assertDoesNotExist()
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-load-一").performClick()
        compose.onNodeWithTag("hud-preset-undo-load").performClick()
        compose.runOnIdle { assertEquals(second.copy(enabled = false), settings.hudSceneLayout) }
    }

    @Test fun deletingLastPresetInVerticalModeKeepsUndoAndSaveAvailable() {
        val preset = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
        var settings by mutableStateOf(AppSettings(hudScenePresets = mapOf("战斗" to preset)))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 700.dp)) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-load-战斗").performClick()
        compose.onNodeWithTag("hud-scene-toggle").performClick()
        compose.onNodeWithTag("hud-preset-delete-战斗").performClick()
        compose.onNodeWithTag("hud-preset-name").performTextReplacement("备用")
        compose.onNodeWithTag("hud-preset-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(preset.copy(enabled = false), settings.hudScenePresets["备用"])
        }
        compose.onNodeWithTag("hud-preset-delete-备用").performClick()
        compose.onNodeWithTag("hud-preset-undo-load").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNull(settings.hudSceneLayout)
            assertTrue(settings.hudScenePresets.isEmpty())
        }
        compose.onNodeWithTag("hud-presets-toggle").assertIsDisplayed()
        compose.onNodeWithTag("hud-preset-save").assertIsNotEnabled()
    }

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
