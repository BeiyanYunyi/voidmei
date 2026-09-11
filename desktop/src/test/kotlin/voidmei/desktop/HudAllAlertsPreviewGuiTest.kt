package voidmei.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*

class HudAllAlertsPreviewGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allAlertSamplesPopulateSeverityRegionsButMissingModeClearsThem() {
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 300, listOf(
            HudRegion("warning", HudRegionContent.ALERTS, 0, 0, 400, 180, fields = listOf("warning")),
            HudRegion("advisory", HudRegionContent.ALERTS, 400, 0, 400, 180, fields = listOf("advisory")),
        )))
        compose.setContent { HudLayoutPreviewWindow(settings, 0) {} }
        compose.onNodeWithTag("hud-preview-all-alerts").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("flight-alert-LOW_FUEL").fetchSemanticsNodes().isNotEmpty() }
        for (alert in FlightAlert.entries.filter { it != FlightAlert.CONNECTION_READY }) {
            val region = if (alert.severity == AlertSeverity.WARNING) "warning" else "advisory"
            compose.onNode(hasTestTag("flight-alert-${alert.name}") and
                hasAnyAncestor(hasTestTag("hud-region-$region"))).assertExists()
        }
        compose.onNodeWithTag("flight-alert-CONNECTION_READY").assertDoesNotExist()
        compose.onAllNodesWithTag("flight-alert-scrollbar").assertCountEquals(2)
        compose.onNodeWithText("缺失数据").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("flight-alerts").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("hud-preview-all-alerts").assertIsNotEnabled()
        compose.onNodeWithText("正常读数").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("flight-alert-LOW_FUEL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hud-preview-all-alerts").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("flight-alert-LOW_FUEL").fetchSemanticsNodes().isEmpty() }
    }
}
