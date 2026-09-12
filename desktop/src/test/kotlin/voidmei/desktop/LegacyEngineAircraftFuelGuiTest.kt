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

class LegacyEngineAircraftFuelGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fuelTargetCanBeSelectedAndCancelledWithoutChangingExistingFlightImport() {
        val parsed = LegacySettingsReader.read("""(panel p (item x :target disableEngineInfoLFuel :type switch-inv :value true))""")
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 200)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two"))))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-engine-fuel-two").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithTag("legacy-engine-fuel-two").performScrollTo().performClick()
        compose.onNodeWithText("two：整机燃油隐藏 → 显示（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithTag("legacy-engine-fuel-none").performScrollTo().performClick()
        compose.onNodeWithText("two：整机燃油隐藏 → 显示（将应用）").assertDoesNotExist()
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithTag("legacy-engine-fuel-two").performScrollTo().performClick()
        click("应用预览设置")
        compose.runOnIdle {
            assertEquals(listOf(one, one.copy(id = "two", showAircraftFuel = true)), current.hudSceneLayout!!.regions)
            assertEquals(parsed.applyTo(original).copy(hudSceneLayout = current.hudSceneLayout), current)
        }
    }
}
