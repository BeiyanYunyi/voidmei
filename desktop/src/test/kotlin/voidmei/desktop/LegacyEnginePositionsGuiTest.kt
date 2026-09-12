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

class LegacyEnginePositionsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pixelDestinationWaitsForScreenSizeWhileRatioDestinationRemainsUsable() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :x 500 :y .25)
            (panel "引擎控制" :x .95 :y .95)""")
        val one = HudRegion("one", HudRegionContent.ENGINE, 10, 20, 300, 200)
        val two = one.copy(id = "two")
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, two)))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun node(key: String, id: String) = compose.onNodeWithTag("legacy-engine-position-$key-$id")
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-engine-position-engineInfoSwitch-one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        node("engineInfoSwitch", "one").assertIsNotEnabled()
        node("enableEngineControl", "two").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextInput("2000")
        compose.onNodeWithText("旧屏幕高度").performScrollTo().performTextInput("1200")
        node("engineInfoSwitch", "two").assertIsNotEnabled()
        node("engineInfoSwitch", "one").performScrollTo().performClick()
        compose.onNodeWithText("动力信息 → one：(10, 20) → (250, 150) dp（将应用）").assertExists()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextClearance()
        node("engineInfoSwitch", "one").assertIsNotEnabled()
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithText("旧屏幕宽度").performScrollTo().performTextInput("2000")
        node("enableEngineControl", "none").performScrollTo().performClick()
        node("enableEngineControl", "two").performScrollTo().performClick()
        click("应用预览设置")
        compose.runOnIdle { assertEquals(listOf(one.copy(x = 250, y = 150), two.copy(x = 700, y = 400)), current.hudSceneLayout!!.regions) }
    }
}
