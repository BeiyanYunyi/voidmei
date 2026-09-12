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

class LegacyEngineControlStyleGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun powerAndControlStyleTargetsCannotCollideAndPreviewMatchesAppliedValues() {
        val parsed = LegacySettingsReader.read("""(panel "引擎控制" :font-size 3)(panel "动力信息" :font-size 6)""")
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 200)
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two")))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun choose(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-control-style-two").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        choose("legacy-power-size-one")
        compose.onNodeWithTag("legacy-control-style-one").assertIsNotEnabled()
        choose("legacy-control-style-two")
        compose.onNodeWithTag("legacy-power-size-two").assertIsNotEnabled()
        compose.onNodeWithText("引擎控制 → two：108 × 13 dp，文字 14.0 sp（将应用）").assertExists()
        choose("legacy-control-style-none")
        compose.onNodeWithTag("legacy-power-size-two").assertIsEnabled()
        choose("legacy-control-style-two")
        click("应用预览设置")
        compose.runOnIdle {
            val regions = current.hudSceneLayout!!.regions
            assertEquals(ReadingTextSizes(15f, 30f, 15f), regions[0].readingTextSizes)
            assertEquals(EngineControlDimensions(108, 13), regions[1].engineControlDimensions)
            assertEquals(ReadingTextSizes(14f, 14f, 14f), regions[1].readingTextSizes)
            assertEquals(ReadingTextWeights(700, 700, 700), regions[1].readingTextWeights)
        }
    }
}
