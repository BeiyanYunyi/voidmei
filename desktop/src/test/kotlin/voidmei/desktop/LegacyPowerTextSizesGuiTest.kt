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

class LegacyPowerTextSizesGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sizeOnlyFileCanCreateTargetAndCancellingCreationDisablesSizeImport() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :font-size 6)""")
        val original = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(original))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun tag(value: String) = compose.onNodeWithTag(value).performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-create-engine-engineInfoSwitch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        tag("legacy-create-engine-engineInfoSwitch")
        tag("legacy-power-size-legacy-power")
        compose.onNodeWithText("动力字号 → 动力信息：15.0 / 30.0 / 15.0 sp（将应用）").assertExists()
        tag("legacy-create-engine-engineInfoSwitch")
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        tag("legacy-create-engine-engineInfoSwitch")
        click("应用预览设置")
        compose.runOnIdle {
            assertEquals(original, current.hudSceneLayout!!.regions.first())
            val region = current.hudSceneLayout!!.regions.last()
            assertEquals(ReadingTextSizes(15f, 30f, 15f), region.readingTextSizes)
            assertEquals(ReadingTextWeights.legacyFlight, region.readingTextWeights)
            assertEquals(1f, region.fontScale)
        }
    }
}
