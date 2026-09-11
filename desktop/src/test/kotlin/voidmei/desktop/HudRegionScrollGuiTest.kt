package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class HudRegionScrollGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overflowIsVisibleAndEngineChangesResetScrollWhileSamplesPreserveIt() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 400, 100)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(440, 500, listOf(region))))
        var flight by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 500.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        val indicator = compose.onNodeWithTag("hud-scroll-indicator")
        fun progress() = indicator.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(0f, progress())
        compose.onNodeWithText("效率").performScrollTo().assertIsDisplayed()
        assertTrue(progress() > .5f)
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(engines = flight.telemetry.engines.map { it.copy(rpm = 2500.0) })) }
        assertTrue(progress() > .5f)
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(regions = listOf(region.copy(engineIndex = 2)))) }
        assertEquals(0f, progress())
        compose.onNodeWithText("发动机 #2").assertIsDisplayed()
        compose.onNodeWithText("效率").performScrollTo()
        compose.runOnIdle { settings = settings.copy(hudEngineFields = settings.hudEngineFields.reversed()) }
        assertEquals(0f, progress())
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.resizeRegion("engine", 400, 450)) }
        indicator.assertDoesNotExist()
        compose.onNodeWithText("效率").assertIsDisplayed()
    }
}
