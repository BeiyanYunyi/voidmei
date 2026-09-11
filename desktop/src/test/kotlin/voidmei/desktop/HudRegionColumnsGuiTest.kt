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

class HudRegionColumnsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionOverridesGlobalColumnsAndEditorCanRestoreInheritance() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 600, 250,
            fields = listOf("ias", "altitude"), readingColumns = 1)
        val scene = HudSceneLayout(600, 550, listOf(one, one.copy(id = "two", y = 300, readingColumns = null)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene, hudReadingColumns = 2))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(550.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(600.dp, 550.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun isSingle(id: String): Boolean {
            fun bounds(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
                .getUnclippedBoundsInRoot()
            return bounds("1500 m").top > bounds("340 km/h").top
        }
        assertTrue(isSingle("one"))
        assertFalse(isSingle("two"))
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-columns-one-2").performScrollTo().performClick()
        assertFalse(isSingle("one"))
        compose.runOnIdle { settings = settings.copy(hudReadingColumns = 1) }
        assertFalse(isSingle("one"))
        assertTrue(isSingle("two"))
        compose.onNodeWithTag("hud-region-columns-one-0").performScrollTo().performClick()
        assertFalse(isSingle("one")) // A wide region chooses two columns despite the one-column global setting.
        compose.onNodeWithTag("hud-region-columns-one-inherit").performScrollTo().performClick()
        assertTrue(isSingle("one"))
        compose.runOnIdle {
            assertNull(settings.hudSceneLayout!!.regions.first().readingColumns)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }
}
