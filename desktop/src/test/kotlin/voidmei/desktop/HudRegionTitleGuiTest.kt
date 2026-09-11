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

class HudRegionTitleGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingTitleUpdatesHudAndPreviewWithoutChangingReadings() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 300, 300, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(300, 300, listOf(region))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(550.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(300.dp)) {
                HudPanel(hudPreviewFlight(), settings, emptyList(), null) {}
                HudSceneDragOverlay(settings.hudSceneLayout!!, { _, _, _ -> })
            }
        } } }
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-title-one").performScrollTo().performTextReplacement("能量与机动")
        compose.onNode(hasText("能量与机动") and hasAnyAncestor(hasTestTag("hud-region-one"))).assertExists()
        compose.onNode(hasText("能量与机动") and hasAnyAncestor(hasTestTag("hud-drag-region-one"))).assertExists()
        compose.onNodeWithText("340 km/h").assertExists()
        compose.runOnIdle {
            assertEquals(region.copy(title = "能量与机动"), settings.hudSceneLayout!!.regions.single())
            settings = SettingsJson.decode(SettingsJson.encode(settings))
        }
        compose.onNodeWithTag("hud-region-title-one").performTextReplacement("")
        compose.onAllNodesWithText("能量与机动").assertCountEquals(0)
        compose.onNodeWithText("340 km/h").assertExists()
        compose.runOnIdle { assertEquals(region, settings.hudSceneLayout!!.regions.single()) }
    }
}
