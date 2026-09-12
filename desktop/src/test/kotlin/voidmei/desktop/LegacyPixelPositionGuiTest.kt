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

class LegacyPixelPositionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pixelPreviewRequiresScreenSizeAndChangesBeforeApply() {
        val imported = LegacySettingsReader.read("""(panel "舵面值" :x 960 :y 540)""")
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(
            HudRegion("controls", HudRegionContent.CONTROLS, 10, 20, 300, 200))))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(currentScene = current.hudSceneLayout, readSettings = { _, _ -> imported }) {
                    current = it.applyTo(current)
                }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("旧屏幕宽度").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextInput("1920")
        compose.onNodeWithText("旧屏幕高度").performScrollTo().performTextInput("1080")
        compose.onNodeWithText("操纵面 → controls：(500, 300) dp").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("操纵面：等待旧屏幕尺寸，位置未选择").assertExists()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextReplacement("3840")
        compose.onNodeWithText("操纵面 → controls：(250, 300) dp").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, current) }
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original.copy(hudSceneLayout = original.hudSceneLayout!!.copy(regions = listOf(
                original.hudSceneLayout!!.regions.single().copy(x = 250, y = 300)))), current)
        }
    }

    @Test fun missingScreenSizeDoesNotBlockUnrelatedSettingsOrCreateRegions() {
        val imported = LegacySettingsReader.read("""(panel "舵面值" :x 960 :y .5
            (item x :target dataPollIntervalMs :type slider :value 80))""")
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 10, 20, 300, 200))))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(currentScene = current.hudSceneLayout, readSettings = { _, _ -> imported }) {
                    current = it.applyTo(current)
                }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("旧屏幕宽度").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("补建缺少的对应分区（1 个）").assertIsNotEnabled()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original.copy(pollIntervalMs = 80), current) }
    }
}
