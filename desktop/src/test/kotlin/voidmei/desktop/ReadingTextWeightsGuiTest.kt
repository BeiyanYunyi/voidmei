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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class ReadingTextWeightsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionWeightsAreIndependentAndUnitCanFollowOrOverrideNumber() {
        val one = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 220, fields = listOf("ias"),
            showFlightStatus = false, showFlightInstruments = false, readingTextSizes = ReadingTextSizes(15f, 30f, 15f))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(500, 500,
            listOf(one, one.copy(id = "two", y = 250)))))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(500.dp).height(650.dp).verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(500.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun layout(id: String, text: String): TextLayoutResult {
            val values = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("hud-region-$id")))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
            return values.single()
        }
        val initial = layout("one", "340 km/h").layoutInput.style.fontWeight
        val labelInitial = layout("one", "IAS").layoutInput.style.fontWeight
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("region-fonts-one").performScrollTo().performClick()
        fun choose(index: Int, weight: String) = compose.onNodeWithTag("reading-weight-one-$index-$weight").performScrollTo().performClick()
        choose(0, "700")
        assertEquals(FontWeight.Bold, layout("one", "IAS").layoutInput.style.fontWeight)
        assertEquals(labelInitial, layout("two", "IAS").layoutInput.style.fontWeight)
        choose(1, "700")
        assertEquals(FontWeight.Bold, layout("one", "340 km/h").layoutInput.style.fontWeight)
        assertEquals(initial, layout("two", "340 km/h").layoutInput.style.fontWeight)
        choose(2, "400")
        assertEquals(FontWeight.Normal, layout("one", "340 km/h").layoutInput.text.spanStyles.single { it.item.fontWeight != null }.item.fontWeight)
        val output = Path.of("build/hud-preview/reading-text-weights.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        choose(2, "inherit")
        assertTrue(layout("one", "340 km/h").layoutInput.text.spanStyles.none { it.item.fontWeight != null })
        compose.onNodeWithTag("reading-weights-one-reset").performScrollTo().performClick()
        assertEquals(initial, layout("one", "340 km/h").layoutInput.style.fontWeight)
        assertEquals(labelInitial, layout("one", "IAS").layoutInput.style.fontWeight)
        compose.runOnIdle {
            assertNull(settings.hudSceneLayout!!.regions.first().readingTextWeights)
            assertEquals(one.readingTextSizes, settings.hudSceneLayout!!.regions.first().readingTextSizes)
        }
    }
}
