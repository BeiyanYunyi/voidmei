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

class LegacyEngineVisibilityGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitMappingPreventsConflictsAndAppliesBothWindowSwitches() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :switch-key engineInfoSwitch :visible false)
            (panel "引擎控制" :switch-key enableEngineControl :visible true)""")
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 200)
        val two = one.copy(id = "two", visible = false)
        val original = AppSettings(hudEnabled = false, hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, two)))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun choose(key: String, id: String) = compose.onNodeWithTag("legacy-engine-visible-$key-$id").performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-engine-visible-engineInfoSwitch-one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        choose("engineInfoSwitch", "one")
        compose.onNodeWithTag("legacy-engine-visible-enableEngineControl-one").assertIsNotEnabled()
        choose("engineInfoSwitch", "none")
        compose.onNodeWithTag("legacy-engine-visible-enableEngineControl-one").assertIsEnabled()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        choose("engineInfoSwitch", "one"); choose("enableEngineControl", "two")
        compose.onNodeWithText("动力信息 → one：显示 → 隐藏（将应用）").assertExists()
        click("应用预览设置")
        compose.runOnIdle {
            assertEquals(listOf(one.copy(visible = false), two.copy(visible = true)), current.hudSceneLayout!!.regions)
            assertEquals(original.copy(hudSceneLayout = current.hudSceneLayout), current)
        }
    }
}
