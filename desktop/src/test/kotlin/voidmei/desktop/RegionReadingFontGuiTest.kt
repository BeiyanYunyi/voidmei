package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.file.Files
import java.nio.file.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class RegionReadingFontGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingARegionUpdatesOnlyItsReadingsAndRestoresInheritance() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 550, 230,
            fields = listOf("ias"), showFlightInstruments = false, showFlightStatus = false)
        var settings by mutableStateOf(AppSettings(hudNumberFont = "monospace", hudSceneLayout =
            HudSceneLayout(550, 500, listOf(one, one.copy(id = "two", y = 260)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(550.dp).height(650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(550.dp, 500.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun family(id: String, text: String): FontFamily? {
            val result = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            return result.single().layoutInput.style.fontFamily
        }
        val inheritedLabel = family("one", "IAS")
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("region-fonts-one").performScrollTo().performClick()
        fun change(tag: String, value: String) {
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
            compose.onNodeWithTag("$tag-apply").performScrollTo().performClick()
        }
        change("region-label-font-one", "serif")
        assertEquals(FontFamily.Serif, family("one", "IAS"))
        assertEquals(inheritedLabel, family("two", "IAS"))
        assertEquals(FontFamily.Monospace, family("one", "340 km/h"))
        change("region-number-font-one", "sans-serif")
        assertEquals(FontFamily.SansSerif, family("one", "340 km/h"))
        assertEquals(FontFamily.Monospace, family("two", "340 km/h"))
        val output = Path.of("build/hud-preview/region-reading-fonts.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        change("region-label-font-one", "Voidmei Missing Font 785cab69")
        assertEquals(FontFamily.Default, family("one", "IAS"))
        change("region-label-font-one", "")
        change("region-number-font-one", "")
        assertEquals(inheritedLabel, family("one", "IAS"))
        assertEquals(FontFamily.Monospace, family("one", "340 km/h"))
        compose.onNodeWithTag("region-label-font-one").performScrollTo().performTextReplacement("x".repeat(201))
        compose.onNodeWithTag("region-label-font-one-apply").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings))) }
    }

    @Test fun legacyFontPreviewRequiresOptIn() {
        val imported = LegacySettingsReader.read("""(panel p (item f :target flightInfoFontC :type combo :value serif))""")
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(700, 500, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 700, 500)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(700.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(settings.hudSceneLayout, readSettings = { _, _ -> imported }) { settings = it.applyTo(settings) }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移飞行标签字体").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("迁移飞行标签字体").performScrollTo().performClick()
        compose.onNodeWithText("飞行标签 → flight：serif（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("serif", settings.hudSceneLayout!!.regions.single().readingLabelFont) }
    }
}
