package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class HudRegionNavigatorGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun jumpAndReturnPreserveUnappliedGeometryDraft() {
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(
            HudRegion("first", HudRegionContent.FLIGHT, 0, 0, 200, 100),
            HudRegion("second", HudRegionContent.ENGINE, 200, 0, 200, 100),
            HudRegion("last", HudRegionContent.MAP, 400, 0, 200, 100, title = "战场地图"))))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            Column(Modifier.size(760.dp, 650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(current) { current = it }
            }
        } }
        compose.onNodeWithTag("hud-region-geometry-first").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-geometry-second").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-geometry-last").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-editor-first").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-geometry-first").assertExists()
        compose.onNodeWithTag("hud-region-editor-first").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-geometry-first").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-editor-first").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-geometry-first").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-input-width-first").performScrollTo().performTextReplacement("unfinished")
        compose.onNodeWithTag("hud-region-directory-back-first").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hud-region-search").assertIsDisplayed().performTextInput("战场 地图")
        compose.onNodeWithTag("hud-region-jump-first").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-jump-last").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hud-region-editor-last").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-directory-back-last").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hud-region-search").assertTextContains("战场 地图")
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithTag("hud-region-jump-first").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hud-region-editor-first").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-input-width-first").performScrollTo().assertTextContains("unfinished")
        compose.onNodeWithTag("hud-region-geometry-apply-first").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(original, current) }
    }

    @Test fun directorySearchTracksRegionEditsAndHiddenEnginesAtNarrowWidth() {
        var regions by mutableStateOf(listOf(
            HudRegion("engine-right", HudRegionContent.ENGINE, 0, 0, 200, 100, engineIndex = 2,
                visible = false, title = "右侧发动机备用监控"),
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 200, 100)))
        var selected: String? = null
        compose.setContent { MaterialTheme {
            Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.surface).testTag("directory-preview")) {
                HudRegionNavigator(regions, true, {}) { selected = it }
            }
        } }
        compose.onNodeWithTag("hud-region-search").performTextInput("ENGINE #2")
        compose.onNodeWithText("发动机 · engine-right · 已隐藏").assertExists()
        compose.onNodeWithTag("hud-region-jump-flight").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-jump-engine-right").performClick()
        compose.runOnIdle { assertEquals("engine-right", selected) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val output = Path.of("build/hud-preview/region-directory-narrow.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(
            compose.onNodeWithTag("directory-preview").captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.onNodeWithTag("hud-region-search").performTextReplacement(" 新标题 ")
        compose.onNodeWithText("没有匹配的区域，请更换关键词或清除搜索。").assertIsDisplayed()
        compose.runOnIdle { regions = regions.map { if (it.id == "engine-right") it.copy(title = "新标题") else it } }
        compose.onNodeWithTag("hud-region-jump-engine-right").assertIsDisplayed()
        compose.runOnIdle { regions = regions.filterNot { it.id == "engine-right" } }
        compose.onNodeWithTag("hud-region-jump-engine-right").assertDoesNotExist()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithTag("hud-region-jump-flight").assertIsDisplayed()
    }
}
