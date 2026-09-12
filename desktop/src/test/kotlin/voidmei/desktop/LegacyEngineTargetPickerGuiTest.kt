package voidmei.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class LegacyEngineTargetPickerGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchPagesAndKeepsCurrentDestinationWhileRespectingConflicts() {
        val regions = (1..32).map { HudRegion("region-$it", HudRegionContent.ENGINE, 0, 0, 100, 100,
            title = "Same", engineIndex = it) }
        var selected by mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Column(Modifier.width(650.dp).height(700.dp).background(Color.White)
            .testTag("picker-preview").padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("动力信息 · 标签字体迁移", style = MaterialTheme.typography.titleLarge)
            Text("选择目标发动机分区")
            LegacyEngineTargetPicker(regions, selected, "target", "不迁移字体", occupiedIds = setOf("region-30")) { selected = it }
        } } }
        compose.onNodeWithTag("target-region-7").assertDoesNotExist()
        compose.onNodeWithTag("target-more").performScrollTo().performClick()
        compose.onNodeWithTag("target-region-7").assertExists()
        compose.onNodeWithTag("target-search").performScrollTo().performTextInput("region-31")
        compose.onNodeWithTag("target-region-31").performScrollTo().performClick()
        compose.onNodeWithTag("target-search").performScrollTo().performTextReplacement("SAME #30")
        compose.onNodeWithTag("target-region-31").assertIsSelected()
        compose.onNodeWithTag("target-region-30").assertIsNotEnabled()
        compose.onNodeWithTag("target-region-1").assertDoesNotExist()
        val out = Path.of("build/hud-preview/legacy-engine-target-search.png")
        Files.createDirectories(out.parent)
        Files.write(out, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("picker-preview")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.onNodeWithTag("target-search").performTextReplacement("no-match")
        compose.onNodeWithTag("target-region-31").assertIsSelected()
        compose.runOnIdle { assertEquals("region-31", selected) }
        compose.onNodeWithTag("target-search-clear").performScrollTo().performClick()
        compose.onNodeWithTag("target-region-7").assertDoesNotExist()
        compose.onNodeWithTag("target-region-31").assertIsSelected()
        compose.onNodeWithTag("target-none").performScrollTo().performClick()
        compose.runOnIdle { assertNull(selected) }
    }
}
