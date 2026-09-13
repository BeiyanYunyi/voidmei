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
import kotlin.test.*
import voidmei.config.*

class HudRegionFontGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun independentFontRetainsSizeAcrossGlobalChangesAndCanRestoreInheritance() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 200, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudFontScale = 1f, hudSceneLayout = HudSceneLayout(500, 450,
            listOf(one, one.copy(id = "two", y = 250)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                GlobalHudFontSizeSettings(settings.hudFontScale) { settings = settings.copy(hudFontScale = it) }
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(500.dp, 450.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun bounds(id: String) = compose.onNode(hasText("340 km/h") and hasAnyAncestor(hasTestTag("hud-region-$id"))).getUnclippedBoundsInRoot()
        fun height(id: String) = bounds(id).let { it.bottom - it.top }
        val initial = height("one")
        val geometry = compose.onNodeWithTag("hud-region-one").getUnclippedBoundsInRoot()
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-font-one-1.5").performScrollTo().performClick()
        val enlarged = height("one")
        assertTrue(enlarged > initial)
        assertEquals(initial, height("two"))
        compose.onNodeWithTag("hud-font-scale").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        assertEquals(enlarged, height("one"))
        assertTrue(height("two") > enlarged)
        assertEquals(geometry, compose.onNodeWithTag("hud-region-one").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("hud-region-font-one-inherit").performScrollTo().performClick()
        assertEquals(height("two"), height("one"))
        compose.runOnIdle {
            assertNull(settings.hudSceneLayout!!.regions.first().fontScale)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }
}
