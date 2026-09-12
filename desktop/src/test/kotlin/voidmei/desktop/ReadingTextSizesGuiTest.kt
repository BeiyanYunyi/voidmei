package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class ReadingTextSizesGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun separateSizesRenderInOneRegionAndEditorCanResetThem() {
        val first = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 220, fields = listOf("ias"),
            showFlightInstruments = false, showFlightStatus = false)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(500, 500,
            listOf(first, first.copy(id = "two", y = 250)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp).height(650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(500.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun layout(id: String, text: String): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("region-fonts-one").performScrollTo().performClick()
        listOf("15", "30", "15").forEachIndexed { i, value ->
            compose.onNodeWithTag("reading-size-one-$i").performScrollTo().performTextReplacement(value)
        }
        compose.onNodeWithTag("reading-sizes-one-apply").performScrollTo().performClick()
        assertEquals(15.sp, layout("one", "IAS").layoutInput.style.fontSize)
        val value = layout("one", "340 km/h")
        assertEquals(30.sp, value.layoutInput.style.fontSize)
        assertEquals(15.sp, value.layoutInput.text.spanStyles.single { it.item.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified }.item.fontSize)
        assertEquals(14.sp, layout("two", "340 km/h").layoutInput.style.fontSize)
        val output = Path.of("build/hud-preview/separate-reading-sizes.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.onNodeWithTag("reading-size-one-1").performScrollTo().performTextReplacement("65")
        compose.onNodeWithTag("reading-sizes-one-apply").assertIsNotEnabled()
        compose.onNodeWithTag("reading-sizes-one-reset").performScrollTo().performClick()
        assertEquals(14.sp, layout("one", "340 km/h").layoutInput.style.fontSize)
        compose.runOnIdle { assertNull(settings.hudSceneLayout!!.regions.first().readingTextSizes) }
    }

    @Test fun measuringSmallerUnitsAllowsColumnsAndChangingSizesResetsWidthHistory() {
        var sizes by mutableStateOf(ReadingTextSizes(12f, 24f, 24f))
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalReadingTextSizes provides sizes) {
            Box(Modifier.width(340.dp)) { FlightReadings(listOf("A" to "1 km/h long", "B" to "2 km/h long"), true,
                unitRanges = mapOf(0 to 2..10, 1 to 2..10)) }
        } } }
        fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top
        assertTrue(top("B") > top("A"))
        compose.runOnIdle { sizes = sizes.copy(unit = 6f) }
        assertEquals(top("A"), top("B"))
        compose.onNodeWithText("1 km/h long").assertIsDisplayed()
    }

    @Test fun legacySizePreviewIsExplicitAndAppliesOnlyAfterSelection() = legacySizePreview(
        """(panel "飞行信息" :font-size 6)""")

    @Test fun scopedLegacyRowUsesTheSameOptionalPreview() = legacySizePreview(
        """(panel "飞行信息" (group appearance (item size :target fontSize :type slider :value 6)))""")

    private fun legacySizePreview(text: String) {
        val imported = LegacySettingsReader.read(text)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(700, 500,
            listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 700, 500, fontScale = 1.5f)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(settings.hudSceneLayout, readSettings = { _, _ -> imported }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移飞行表格字号").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移飞行表格字号").performScrollTo().performClick()
        compose.onNodeWithText("flight 的文字缩放将设为 100%（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ReadingTextSizes(15f, 30f, 15f), settings.hudSceneLayout!!.regions.single().readingTextSizes)
            assertEquals(1f, settings.hudSceneLayout!!.regions.single().fontScale)
        }
    }
}
