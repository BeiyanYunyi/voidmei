package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class EngineInstrumentLabelFontGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun regionOverridesReachInstrumentCaptionsAndResetWithoutAffectingOtherRegion() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 500, 500,
            fields = listOf("radiator", "compressor"))
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 500, listOf(one, one.copy(id = "two", x = 500))))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Box(Modifier.size(1000.dp, 500.dp)) { HudLayoutPreview(settings) } } }
        fun style(id: String, text: String): TextStyle {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style
        }
        val labels = listOf("发动机 #1", "1 号水散热器，满刻度 100%", "1 号增压器 · 1 / 2 档")
        val initial = labels.associateWith { style("one", it) }
        val numberBefore = style("one", "35 %")
        compose.runOnIdle { settings = original.copy(hudSceneLayout = original.hudSceneLayout!!.copy(regions = listOf(
            one.copy(readingLabelFont = "monospace", readingTextSizes = ReadingTextSizes(label = 18f),
                readingTextWeights = ReadingTextWeights(label = 700)), original.hudSceneLayout!!.regions[1]))) }
        for (text in labels) {
            val actual = style("one", text)
            assertEquals(FontFamily.Monospace, actual.fontFamily)
            assertEquals(18.sp, actual.fontSize)
            assertEquals(FontWeight.Bold, actual.fontWeight)
            assertEquals(initial.getValue(text), style("two", text))
        }
        assertEquals(numberBefore.fontFamily, style("one", "35 %").fontFamily)
        assertEquals(numberBefore.fontWeight, style("one", "35 %").fontWeight)
        val output = Path.of("build/hud-preview/engine-instrument-label-fonts.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.runOnIdle { settings = original }
        for (text in labels) assertEquals(initial.getValue(text), style("one", text))
    }
}
