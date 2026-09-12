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
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voidmei.config.*
import kotlin.test.*

class LegacyHudPositionGuiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun positionOnlyFileRequiresOptInAndSavesPreviewedCoordinates() {
        val file = temporary.root.toPath().resolve("ui_layout.user.cfg")
        val text = """(panel "舵面值" :x .9 :y .5 :alpha 10 :visible false)"""
        Files.writeString(file, text)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(
            HudRegion("controls", HudRegionContent.CONTROLS, 10, 20, 300, 200))))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(currentScene = current.hudSceneLayout, chooseFile = { file.toString() }) {
                    current = it.applyTo(current)
                }
            }
        } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("选择旧版设置文件").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移已有分区位置").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("操纵面 → controls：(700, 300) dp").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(original, current) }
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        val scene = original.hudSceneLayout!!
        val expected = original.copy(hudSceneLayout = scene.copy(regions = listOf(scene.regions.single().copy(x = 700, y = 300))))
        compose.runOnIdle { assertEquals(expected, current) }
        val store = SettingsStore(temporary.root.toPath().resolve("settings.json"))
        store.save(current)
        assertEquals(expected, store.load().settings)
        assertEquals(text, Files.readString(file))
    }
    @Test fun missingRegionPreviewCanBeCancelledThenAppliedWithoutChangingExistingRegion() {
        val imported = LegacySettingsReader.read("""(panel "舵面值" :x .9 :y .5)""")
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
        compose.waitUntil(5000) { compose.onAllNodesWithText("补建缺少的对应分区（1 个）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        val create = isToggleable() and hasText("补建缺少的对应分区（1 个）")
        compose.onNode(create).performScrollTo().performClick()
        compose.onNodeWithText("操纵面 → region-1：(560, 300) dp").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, current) }
        compose.onNode(create).performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNode(create).performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            val regions = current.hudSceneLayout!!.regions
            assertEquals(original.hudSceneLayout!!.regions.single(), regions.first())
            assertEquals(HudRegionContent.CONTROLS, regions.last().content)
            assertEquals(560, regions.last().x)
            assertEquals(300, regions.last().y)
            assertEquals(2, regions.size)
        }
    }

}
