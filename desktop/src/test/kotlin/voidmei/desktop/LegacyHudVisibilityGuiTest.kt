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

class LegacyHudVisibilityGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibilityOnlyImportRequiresExplicitSelectionAndCanBeCancelled() {
        val imported = LegacySettingsReader.read("""(panel p :switch-key enableAxis :visible false)""")
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
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移独立面板显示开关").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移独立面板显示开关").performScrollTo().performClick()
        compose.onNodeWithText("操纵面 → controls：隐藏（将应用）").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, current) }
        compose.onNodeWithText("迁移独立面板显示开关").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移独立面板显示开关").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original.copy(hudSceneLayout = original.hudSceneLayout!!.copy(
            regions = listOf(original.hudSceneLayout!!.regions.single().copy(visible = false)))), current) }
    }

    @Test fun selectedVisibilityAppliesToNewRegionAndDoesNotActivateHud() {
        val imported = LegacySettingsReader.read("""(panel "舵面值" :x .8 :y .5
            (item x :target enableAxis :type switch :value false))""")
        var current by mutableStateOf(AppSettings(hudEnabled = false,
            hudSceneLayout = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 200, 100)))))
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(currentScene = current.hudSceneLayout, readSettings = { _, _ -> imported }) {
                    current = it.applyTo(current)
                }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移独立面板显示开关").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("迁移独立面板显示开关").assertIsNotEnabled()
        compose.onNodeWithText("补建缺少的对应分区（1 个）").performScrollTo().performClick()
        compose.onNodeWithText("迁移独立面板显示开关").performScrollTo().performClick()
        compose.onNodeWithText("操纵面 → region-1：隐藏（将应用）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(current.hudEnabled)
            assertEquals(2, current.hudSceneLayout!!.regions.size)
            assertFalse(current.hudSceneLayout!!.regions.last().visible)
        }
    }
}
