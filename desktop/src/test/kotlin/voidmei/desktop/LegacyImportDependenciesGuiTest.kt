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

class LegacyImportDependenciesGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancellingCreationDisablesDependentImportsAndReenablingAppliesThemTogether() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x .2 :y .3 :font serif :font-size 6
            (item e :target flightInfoEdge :type switch :value true)
            (item c :target flightInfoColumn :type slider :value 3))""")
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(
            HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 300, 200))))
        var current by mutableStateOf(original)
        var applied = 0
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> imported }) {
                applied++; current = it.applyTo(current)
            }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        click("导入旧版设置")
        click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithText("补建缺少的对应分区（1 个）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        click("补建缺少的对应分区（1 个）")
        for (label in listOf("迁移飞行信息列数", "迁移飞行标签字体", "迁移飞行表格字号", "迁移独立面板边框开关")) click(label)
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        click("迁移已有分区位置") // The label itself must toggle the row and cancel creation.
        compose.onNodeWithText("位置预览：未选择，不修改当前位置").assertExists()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(original, current); assertEquals(0, applied) }
        click("补建缺少的对应分区（1 个）")
        compose.onNodeWithText("位置预览：将应用").assertExists()
        click("应用预览设置")
        compose.runOnIdle {
            assertEquals(1, applied)
            val scene = current.hudSceneLayout!!
            assertEquals(original.hudSceneLayout!!.regions.single(), scene.regions.first())
            val added = scene.regions.last()
            assertEquals(200, added.x)
            assertEquals(130, added.y) // The 470 dp region is clamped inside the 600 dp canvas.
            assertEquals(3, added.readingColumns)
            assertEquals("serif", added.readingLabelFont)
            assertEquals(ReadingTextSizes(15f, 30f, 15f), added.readingTextSizes)
            assertEquals(ReadingTextWeights.legacyFlight, added.readingTextWeights)
            assertTrue(added.borderEnabled)
            assertEquals(current, SettingsJson.decode(SettingsJson.encode(current)))
        }
    }
}
