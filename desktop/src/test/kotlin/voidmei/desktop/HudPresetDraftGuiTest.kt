package voidmei.desktop

import androidx.compose.foundation.layout.*
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
import kotlin.test.assertEquals
import voidmei.config.*

class HudPresetDraftGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadAndUndoDiscardDraftsEvenWithIdenticalGeometryAndIds() {
        val scene = HudSceneLayout(500, 300, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("同坐标" to scene)))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp).verticalScroll(rememberScrollState())) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        fun draft() {
            compose.onNodeWithTag("hud-canvas-width").performScrollTo().performTextReplacement("700")
            compose.onNodeWithTag("hud-region-geometry-one").performScrollTo().performClick()
            compose.onNodeWithTag("hud-region-input-x-one").performScrollTo().performTextReplacement("100")
        }
        fun assertReset() {
            compose.onNodeWithTag("hud-canvas-width").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("500")))
            compose.onNodeWithTag("hud-region-input-x-one").assertDoesNotExist()
            compose.onNodeWithTag("hud-region-geometry-one").performScrollTo().performClick()
            compose.onNodeWithTag("hud-region-input-x-one").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("0")))
            compose.onNodeWithTag("hud-region-geometry-apply-one").assertIsNotEnabled()
        }
        draft()
        compose.onNodeWithTag("hud-presets-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("hud-preset-load-同坐标").performScrollTo().performClick()
        assertReset()
        compose.onNodeWithTag("hud-region-input-x-one").performScrollTo().performTextReplacement("150")
        compose.onNodeWithTag("hud-canvas-width").performScrollTo().performTextReplacement("800")
        compose.onNodeWithTag("hud-preset-undo-load").performScrollTo().performClick()
        assertReset()
        compose.runOnIdle { assertEquals(scene, settings.hudSceneLayout) }
    }
}
