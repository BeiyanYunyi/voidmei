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
import java.nio.file.Files
import kotlin.test.*
import voidmei.config.*

class HudPresetTransferGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingSubsetFitsRemainingCapacityAndIgnoresExcludedInvalidName() {
        val root = Files.createTempDirectory("voidmei-preset-subset-")
        try {
            val scene = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
            val input = root.resolve("input.json")
            writeHudPresets(input, mapOf("巡航" to scene, "作战" to scene.copy(enabled = false)))
            val original = AppSettings(hudSceneLayout = scene,
                hudScenePresets = (1..15).associate { "已有$it" to scene })
            var settings by mutableStateOf(original)
            compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 900.dp)) {
                HudPresetTransfer(settings, { settings = it }, { input.toString() }, { null })
            } } }
            compose.onNodeWithTag("hud-presets-import").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-presets-import-apply").fetchSemanticsNodes().isNotEmpty() }
            val apply = compose.onNodeWithTag("hud-presets-import-apply")
            apply.assertIsNotEnabled()
            compose.onNodeWithTag("hud-presets-import-rename-作战").performClick()
            compose.onNodeWithTag("hud-presets-import-name-作战").performTextReplacement("")
            compose.onNodeWithTag("hud-presets-import-select-作战").performClick().assertIsOff()
            compose.onNodeWithTag("hud-presets-import-name-作战").assertIsNotEnabled()
            apply.assertIsEnabled()
            compose.onNodeWithTag("hud-presets-import-select-巡航").performClick().assertIsOff()
            apply.assertIsNotEnabled()
            compose.onNodeWithTag("hud-presets-import-select-巡航").performClick().assertIsOn()
            compose.onNodeWithTag("hud-presets-import-select-作战").performClick().assertIsOn()
            apply.assertIsNotEnabled() // The retained invalid draft matters again when reselected.
            compose.onNodeWithTag("hud-presets-import-select-作战").performClick()
            compose.runOnIdle { assertEquals(original, settings) }
            apply.performClick()
            compose.runOnIdle { assertEquals(original.copy(hudScenePresets = original.hudScenePresets + ("巡航" to scene)), settings) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun renameDuringImportPreservesBothLayoutsAndRejectsDuplicateTargets() {
        val root = Files.createTempDirectory("voidmei-preset-rename-")
        try {
            val scene = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
            val incoming = scene.copy(enabled = false)
            val input = root.resolve("input.json")
            writeHudPresets(input, mapOf("巡航" to incoming, "作战" to scene))
            val original = AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("巡航" to scene))
            var settings by mutableStateOf(original)
            compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 900.dp)) {
                HudPresetTransfer(settings, { settings = it }, { input.toString() }, { null })
            } } }
            compose.onNodeWithTag("hud-presets-import").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-presets-import-apply").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("hud-presets-import-rename-巡航").performClick()
            val field = compose.onNodeWithTag("hud-presets-import-name-巡航")
            for (invalid in listOf(" ", "x".repeat(81), "作战")) {
                field.performTextReplacement(invalid)
                compose.onNodeWithTag("hud-presets-import-apply").assertIsNotEnabled()
            }
            field.performTextReplacement(" 备用巡航 ")
            compose.onNodeWithTag("hud-presets-import-apply").assertIsEnabled()
            compose.runOnIdle { assertEquals(original, settings) }
            compose.onNodeWithTag("hud-presets-import-apply").performClick()
            compose.runOnIdle {
                assertEquals(original.copy(hudScenePresets = mapOf("巡航" to scene,
                    "备用巡航" to incoming, "作战" to scene)), settings)
            }
            assertEquals(mapOf("巡航" to incoming, "作战" to scene), readHudPresets(input))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun importPreviewsConflictsAndMergesIntoLatestSettingsWithoutLoading() {
        val root = Files.createTempDirectory("voidmei-preset-ui-")
        try {
            val scene = HudSceneLayout(400, 240, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 400, 240)))
            val backup = mapOf("已有" to scene, "新布局" to scene.copy(enabled = false))
            val input = root.resolve("input.json")
            val output = root.resolve("output.json")
            writeHudPresets(input, backup)
            val original = AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("已有" to scene.resizeCanvas(600, 400)))
            var settings by mutableStateOf(original)
            var selected: String? = input.toString()
            compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 850.dp)) {
                HudPresetTransfer(settings, { settings = it }, { selected }, { output.toString() })
            } } }
            compose.onNodeWithTag("hud-presets-import").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-presets-import-apply").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("已有 · 1 区域 · 400 × 240 dp · 同名，跳过").assertIsDisplayed()
            compose.runOnIdle { assertEquals(original, settings); settings = settings.copy(voiceVolume = 25,
                hudScenePresets = (1..16).associate { "占位$it" to scene }) }
            compose.onNodeWithTag("hud-presets-import-apply").assertIsNotEnabled()
            compose.runOnIdle { settings = settings.copy(hudScenePresets = original.hudScenePresets) }
            compose.onNodeWithTag("hud-presets-import-apply").performClick()
            compose.runOnIdle {
                assertEquals(original.copy(voiceVolume = 25,
                    hudScenePresets = original.hudScenePresets + ("新布局" to backup.getValue("新布局"))), settings)
            }
            compose.onNodeWithTag("hud-presets-export").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已导出 2 套预设：$output").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(settings.hudScenePresets, readHudPresets(output))
            selected = null
            compose.onNodeWithTag("hud-presets-import").performClick()
            compose.onNodeWithTag("hud-presets-import-apply").assertDoesNotExist()
            Files.writeString(input, "{}")
            selected = input.toString()
            compose.onNodeWithTag("hud-presets-import").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("读取失败：不是 VoidMei HUD 预设文件").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("hud-presets-import-apply").assertDoesNotExist()
        } finally { root.toFile().deleteRecursively() }
    }
}
