package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.*

class HudRegionGeometryGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appliesGeometryAtomicallyAndRetainsConcurrentStyleChanges() {
        val original = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 200, engineIndex = 2, fields = listOf("rpm"))
        var scene by mutableStateOf(HudSceneLayout(1000, 600, listOf(original)))
        compose.setContent { MaterialTheme { Column(Modifier.size(500.dp, 600.dp)) {
            HudRegionGeometrySettings(scene.regions.single(), scene) { scene = scene.copy(regions = listOf(it)) }
        } } }
        compose.onNodeWithTag("hud-region-geometry-one").performClick()
        fun input(name: String, value: String) = compose.onNodeWithTag("hud-region-input-$name-one").performTextReplacement(value)
        val apply = compose.onNodeWithTag("hud-region-geometry-apply-one")
        apply.assertIsNotEnabled()
        input("x", "900")
        apply.assertIsNotEnabled() // Moving the old 400 dp region here cannot fit.
        input("width", "100")
        input("y", "500")
        input("height", "100")
        apply.assertIsEnabled()
        compose.runOnIdle {
            assertEquals(original, scene.regions.single())
            scene = scene.copy(regions = listOf(original.copy(backgroundAlpha = .25f, title = "发动机二")))
        }
        apply.performClick()
        compose.runOnIdle {
            assertEquals(original.copy(x = 900, y = 500, width = 100, height = 100, backgroundAlpha = .25f, title = "发动机二"), scene.regions.single())
            assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
        }
        for (value in listOf("", "-1", "1.5", "999999999999", "1000")) {
            input("x", value)
            apply.assertIsNotEnabled()
        }
        input("x", "800")
        apply.assertIsEnabled()
        compose.runOnIdle { scene = scene.resizeCanvas(850, 600) }
        apply.assertIsNotEnabled() // External layout change synchronizes the position draft.
        input("x", "800")
        apply.assertIsNotEnabled() // Revalidated against the new canvas width.
        input("width", "80")
        apply.assertIsNotEnabled()
        input("x", "770")
        apply.assertIsEnabled()
        apply.performClick()
        compose.runOnIdle { assertEquals(770, scene.regions.single().x); assertEquals(80, scene.regions.single().width) }
    }
}
