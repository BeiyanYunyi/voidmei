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

class HudRegionDuplicateGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun copiedEngineKeepsConfigurationAndCanBeEditedIndependently() {
        val source = HudRegion("source", HudRegionContent.ENGINE, 20, 30, 300, 200,
            .25f, .75f, 2, listOf("rpm", "future"), title = "右发动机", readingColumns = 1)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 600, listOf(source))))
        compose.setContent { MaterialTheme {
            Column(Modifier.size(600.dp, 600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
        } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-duplicate-source").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(source, source.copy(id = "region-1", x = 36, y = 46)), settings.hudSceneLayout!!.regions)
        }
        compose.onNodeWithTag("hud-region-editor-region-1").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-engine-region-1").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("hud-region-title-region-1").performScrollTo().performTextReplacement("左发动机")
        compose.onNodeWithTag("hud-region-columns-region-1-2").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(source, settings.hudSceneLayout!!.regions.first())
            assertEquals(source.copy(id = "region-1", x = 36, y = 46, engineIndex = 1, title = "左发动机", readingColumns = 2),
                settings.hudSceneLayout!!.regions.last())
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }
}
