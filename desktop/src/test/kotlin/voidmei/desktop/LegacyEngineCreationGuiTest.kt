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

class LegacyEngineCreationGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun createPreviewEnablesDependentImportsAndCancellationDisablesThem() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :x .25 :y .2 :switch-key engineInfoSwitch :visible false
            (item c :target hudColumns :type slider :value 3))
            (panel "引擎控制" :x .5 :y .1 :switch-key enableEngineControl :visible true)""")
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun tag(value: String) = compose.onNodeWithTag(value).performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-create-engine-engineInfoSwitch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        tag("legacy-create-engine-engineInfoSwitch")
        tag("legacy-engine-columns-legacy-power")
        tag("legacy-engine-visible-engineInfoSwitch-legacy-power")
        tag("legacy-engine-position-engineInfoSwitch-legacy-power")
        tag("legacy-create-engine-engineInfoSwitch")
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        tag("legacy-create-engine-engineInfoSwitch")
        tag("legacy-create-engine-enableEngineControl")
        tag("legacy-engine-position-enableEngineControl-legacy-engine-controls")
        click("应用预览设置")
        compose.runOnIdle {
            val regions = current.hudSceneLayout!!.regions.filter { it.content == HudRegionContent.ENGINE }
            assertEquals(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100), current.hudSceneLayout!!.regions.first())
            assertEquals(2, regions.size)
            val power = regions.single { it.id == "legacy-power" }
            assertEquals(3, power.readingColumns); assertFalse(power.visible)
            assertEquals(250, power.x); assertEquals(120, power.y)
            assertEquals(listOf(1, 1), regions.map { it.engineIndex })
            assertNotEquals(regions[0].fields, regions[1].fields)
        }
    }
}
