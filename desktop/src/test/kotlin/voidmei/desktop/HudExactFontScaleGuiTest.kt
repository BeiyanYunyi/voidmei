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

class HudExactFontScaleGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exactScaleChangesOnlyTheSelectedRegionAndCanInheritAgain() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 200, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(500, 450,
            listOf(one, one.copy(id = "two", y = 250)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp).height(650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(500.dp, 450.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun height(id: String) = compose.onNode(hasText("340 km/h") and hasAnyAncestor(hasTestTag("hud-region-$id")))
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val initial = height("one")
        val geometry = compose.onNodeWithTag("hud-region-one").getUnclippedBoundsInRoot()
        compose.expandHudRegionEditors()
        val tag = "hud-region-font-exact-one"
        compose.onNodeWithTag("$tag-toggle").performScrollTo().performClick()
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("137.5")
        compose.onNodeWithTag("$tag-apply").performScrollTo().performClick()
        val customHeight = height("one")
        assertTrue(customHeight > initial)
        assertEquals(initial, height("two"))
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("143.25")
        compose.runOnIdle {
            assertEquals(1.375f, settings.hudSceneLayout!!.regions.first().fontScale)
            settings = settings.copy(hudFontScale = 1.8f)
        }
        compose.onNodeWithTag(tag).assertTextContains("143.25")
        assertEquals(customHeight, height("one"))
        assertTrue(height("two") > customHeight)
        assertEquals(geometry, compose.onNodeWithTag("hud-region-one").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("hud-region-font-one-inherit").performScrollTo().performClick()
        assertEquals(height("two"), height("one"))
        compose.runOnIdle { assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings))) }
    }

    @Test fun invalidDraftsNeverApplyAndGlobalExactValueIsVisible() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.openHudSettingsPage("font")
        val tag = "hud-font-exact"
        compose.onNodeWithTag("$tag-toggle").performScrollTo().performClick()
        for (invalid in listOf("", "74.99", "200.01", "NaN", "Infinity", "1e2", "125.555")) {
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(invalid)
            compose.onNodeWithTag("$tag-apply").assertIsNotEnabled()
        }
        compose.runOnIdle { assertEquals(1f, settings.hudFontScale) }
        for (value in listOf("75", "200", "112.25")) {
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
            compose.onNodeWithTag("$tag-apply").performScrollTo().performClick()
            compose.onNodeWithText("全局 HUD 文字大小 $value%").assertExists()
        }
        compose.runOnIdle { assertEquals(1.1225f, settings.hudFontScale) }
    }
}
