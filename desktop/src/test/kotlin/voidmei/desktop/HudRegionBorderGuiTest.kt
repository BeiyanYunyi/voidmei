package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class HudRegionBorderGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun borderPixelsChangeWithoutMovingReadingsAndOpacityIsIndependent() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 220, fields = listOf("ias"),
            showFlightInstruments = false, showFlightStatus = false, backgroundAlpha = 1f)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(500, 500,
            listOf(one, one.copy(id = "two", y = 250)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp).height(650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(500.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        val region = compose.onNodeWithTag("hud-region-one")
        fun edge() = region.captureToImage().toPixelMap().let { it[(it.width * 6.5 / 500).toInt(), it.height / 2] }
        fun reading() = compose.onNode(hasText("340 km/h") and hasAnyAncestor(hasTestTag("hud-region-one"))).getUnclippedBoundsInRoot()
        val initialEdge = edge()
        val initialReading = reading()
        val initialBounds = region.getUnclippedBoundsInRoot()
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-border-one").performScrollTo().performClick()
        assertNotEquals(initialEdge, edge())
        assertEquals(initialReading, reading())
        assertEquals(initialBounds, region.getUnclippedBoundsInRoot())
        val output = Path.of("build/hud-preview/region-border.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene").captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.onNodeWithTag("hud-region-border-alpha-one").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(initialEdge, edge())
        compose.runOnIdle {
            assertTrue(settings.hudSceneLayout!!.regions.first().borderEnabled)
            assertEquals(1f, settings.hudSceneLayout!!.regions.first().backgroundAlpha)
            assertFalse(settings.hudSceneLayout!!.regions[1].borderEnabled)
        }
    }

    @Test fun legacyBorderPreviewRequiresSelectionAndApplies() {
        val imported = LegacySettingsReader.read("""(panel p (item e :target flightInfoEdge :type switch :value true))""")
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(700, 500,
            listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 700, 500)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(settings.hudSceneLayout, readSettings = { _, _ -> imported }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移独立面板边框开关").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移独立面板边框开关").performScrollTo().performClick()
        compose.onNodeWithText("飞行读数 → flight：边框开启（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settings.hudSceneLayout!!.regions.single().borderEnabled) }
    }
}
