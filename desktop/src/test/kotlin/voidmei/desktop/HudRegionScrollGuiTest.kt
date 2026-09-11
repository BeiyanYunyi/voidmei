package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.*
import voidmei.telemetry.*

class HudRegionScrollGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewMarksOverflowUntilLayoutFitsButLiveHudHasNoBadge() {
        var region by mutableStateOf(HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 400, 100,
            contentAlpha = 0f))
        var preview by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 500.dp)) {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(440, 500, listOf(region)))
            if (preview) HudLayoutPreview(settings)
            else HudPanel(hudPreviewFlight(), settings, emptyList(), null) {}
        } } }
        val badge = compose.onNodeWithTag("hud-region-overflow-engine")
        badge.assertIsDisplayed()
        compose.runOnIdle { region = region.copy(height = 450) }
        badge.assertDoesNotExist()
        compose.runOnIdle { region = region.copy(height = 100) }
        badge.assertIsDisplayed()
        compose.runOnIdle { region = region.copy(fields = listOf("rpm"), showEngineInstruments = false) }
        badge.assertDoesNotExist()
        compose.runOnIdle { region = region.copy(fields = null); preview = false }
        badge.assertDoesNotExist()
        compose.onNodeWithTag("hud-scroll-indicator").assertExists()
        compose.runOnIdle { preview = true }
        badge.assertIsDisplayed()
        compose.runOnIdle { region = region.copy(visible = false) }
        badge.assertDoesNotExist()
    }

    @Test fun reflowStartsAtTopButTelemetryAndPlacementKeepScrollPosition() {
        var region by mutableStateOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 400, 180,
            fields = HudField.entries.map { it.id }, showFlightInstruments = false))
        var flight by mutableStateOf(hudPreviewFlight())
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 400.dp)) {
            HudPanel(flight, settings.copy(hudSceneLayout = HudSceneLayout(600, 400, listOf(region))), emptyList(), null) {}
        } } }
        fun progress() = compose.onNodeWithTag("hud-scroll-indicator").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        fun scroll() {
            compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("hud-region-flight")))
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 160f) }
            compose.waitForIdle()
            assertTrue(progress() > 0f)
        }
        scroll()
        val before = progress()
        compose.runOnIdle {
            region = region.copy(x = 10, backgroundAlpha = .25f)
            flight = flight.copy(telemetry = flight.telemetry.copy(iasKmh = 350.0))
        }
        assertEquals(before, progress())
        val changes: List<() -> Unit> = listOf(
            { settings = settings.copy(hudReadingColumns = 2) },
            { region = region.copy(fontScale = 1.5f) },
            { region = region.copy(width = 450) },
            { region = region.copy(height = 200) },
            { region = region.copy(showFlightStatus = false) },
            { region = region.copy(showFlightInstruments = true) },
        )
        changes.forEach { change ->
            compose.runOnIdle(change)
            assertEquals(0f, progress())
            scroll()
        }
    }

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
