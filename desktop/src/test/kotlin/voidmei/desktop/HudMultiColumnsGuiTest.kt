package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import java.nio.file.Files
import java.nio.file.Path
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

class HudMultiColumnsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitColumnsRespectOrderingAndNarrowCellsDoNotOverlap() {
        var columns by mutableStateOf(3)
        var width by mutableStateOf(700.dp)
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalReadingColumns provides columns) {
                Box(Modifier.width(width).background(Color(0xFF111820)).testTag("multi-column-preview")) { FlightReadings((0..16).map { "指标$it" to "$it m" }, true) }
            }
        } }
        fun bounds(index: Int) = compose.onNodeWithText("指标$index").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds(0).top, bounds(2).top)
        assertTrue(bounds(3).top > bounds(0).top)
        val output = Path.of("build/hud-preview/three-column-readings.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("multi-column-preview")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.runOnIdle { columns = 16 }
        assertEquals(bounds(0).top, bounds(15).top)
        assertTrue(bounds(16).top > bounds(0).top)
        compose.runOnIdle { width = 240.dp }
        for (i in 0..14) assertTrue(bounds(i).right <= bounds(i + 1).left + 1f)
        compose.onNodeWithText("16 m").assertIsDisplayed()
    }

    @Test fun customInputValidatesAndRegionOverrideRemainsIndependent() {
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(700, 500, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 700, 500)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(current) { current = it }
        } } }
        compose.onNodeWithText("HUD 字段设置").performScrollTo().performClick()
        compose.onNodeWithTag("hud-columns-custom-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("hud-columns-custom").performScrollTo().performTextReplacement("17")
        compose.onNodeWithTag("hud-columns-custom-apply").assertIsNotEnabled()
        compose.onNodeWithTag("hud-columns-custom").performTextReplacement("4")
        compose.onNodeWithTag("hud-columns-custom-apply").performScrollTo().performClick()
        compose.onNodeWithText("调整分区位置与透明度").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-columns-custom-flight-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-columns-custom-flight").performScrollTo().performTextReplacement("12")
        compose.onNodeWithTag("hud-region-columns-custom-flight-apply").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(4, current.hudReadingColumns)
            assertEquals(12, current.hudSceneLayout!!.regions.single().readingColumns)
        }
    }

    @Test fun legacyFlightColumnsPreviewRequiresOptIn() {
        val imported = LegacySettingsReader.read("""(panel p (item x :target flightInfoColumn :type slider :value 8))""")
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(700, 500, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 700, 500)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> imported }) { current = it.applyTo(current) }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移飞行信息列数").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移飞行信息列数").performScrollTo().performClick()
        compose.onNodeWithText("飞行信息 → flight：8 列（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(8, current.hudSceneLayout!!.regions.single().readingColumns) }
    }
}
