package voidmei.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*
import kotlin.test.*

class HudSceneGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoEngineRegionsRemainIndependentWhenOneEngineDisappears() {
        val layout = HudSceneLayout(800, 300, listOf(
            HudRegion("one", HudRegionContent.ENGINE, 0, 0, 350, 280, engineIndex = 1),
            HudRegion("two", HudRegionContent.ENGINE, 450, 0, 350, 280, engineIndex = 2)))
        val settings = AppSettings(hudSceneLayout = layout, hudEngineFields = listOf("rpm", "water_temperature"))
        var flight by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("2400 RPM").assertIsDisplayed()
        compose.onNodeWithText("2300 RPM").assertIsDisplayed()
        compose.onNodeWithText("95.0 °C").assertIsDisplayed()
        compose.onNodeWithText("90.0 °C").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(engines = flight.telemetry.engines.filter { it.index == 1 })) }
        compose.onNodeWithText("2400 RPM").assertIsDisplayed()
        compose.onNodeWithText("2300 RPM").assertDoesNotExist()
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
    }

    @Test fun menuAddsEngineRegionsValidatesIndexAndRemovesOnlySelectedRegion() {
        val original = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 240, 120, .3f)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 400, listOf(original))))
        compose.setContent { MaterialTheme {
            Column(Modifier.size(600.dp, 600.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
        } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-remove-flight").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("hud-region-add-ENGINE").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-editor-region-1").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-engine-region-1").performScrollTo().performTextReplacement("0")
        compose.runOnIdle { assertEquals(1, settings.hudSceneLayout!!.regions.last().engineIndex) }
        compose.onNodeWithText("请输入正整数；暂未应用此输入。").assertExists()
        compose.onNodeWithTag("hud-region-engine-region-1").performTextReplacement("2")
        compose.runOnIdle { assertEquals(2, settings.hudSceneLayout!!.regions.last().engineIndex) }
        compose.onNodeWithTag("hud-region-add-ENGINE").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(2, 1), settings.hudSceneLayout!!.regions.drop(1).map { it.engineIndex })
            settings = SettingsJson.decode(SettingsJson.encode(settings))
        }
        compose.onNodeWithTag("hud-region-remove-region-1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("flight", "region-2"), settings.hudSceneLayout!!.regions.map { it.id })
            assertEquals(original, settings.hudSceneLayout!!.regions.first())
        }
    }

    @Test fun menuChangesPersistAndSwitchingBackRetainsRegions() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme {
            Column(Modifier.size(600.dp, 600.dp).then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                HudSceneSettings(settings) { settings = it }
            }
        } }
        compose.onNodeWithTag("hud-scene-toggle").performClick()
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-background-flight").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.25f) }
        compose.runOnIdle { assertEquals(.25f, settings.hudSceneLayout!!.regions.first().backgroundAlpha) }
        compose.onNodeWithTag("hud-scene-toggle").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(settings.hudSceneLayout!!.enabled)
            settings = SettingsJson.decode(SettingsJson.encode(settings))
        }
        compose.onNodeWithTag("hud-scene-toggle").performClick()
        compose.runOnIdle {
            assertTrue(settings.hudSceneLayout!!.enabled)
            assertEquals(.25f, settings.hudSceneLayout!!.regions.first().backgroundAlpha)
            assertFalse(settings.hudClickThrough)
        }
    }

    @Test fun multipleRegionsAndEditsKeepOneNativeHudWindow() {
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout.initial(AppSettings(hudEngineIndex = 2)) ))
        var native: java.awt.Window? = null
        var applied: HudSceneLayout? = null
        compose.setContent {
            val state = androidx.compose.ui.window.rememberWindowState(width = 900.dp, height = 600.dp)
            HudWindow(state, {}, compatibilityMode = true, clickThrough = true) {
                val current = settings
                SideEffect { native = window; applied = current.hudSceneLayout }
                MaterialTheme { HudPanel(hudPreviewFlight(), current, emptyList(), null) {} }
            }
        }
        compose.waitUntil(5000) { native?.isShowing == true }
        val original = assertNotNull(native)
        fun count() = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
            .count { it.isDisplayable && it.title == "VoidMei HUD" }
        compose.runOnIdle { assertEquals(1, count()) }
        compose.waitUntil(5000) { applied == settings.hudSceneLayout }
        compose.runOnIdle {
            val scene = settings.hudSceneLayout!!
            settings = settings.copy(hudSceneLayout = scene.copy(regions = scene.regions.map {
                if (it.content == HudRegionContent.ENGINE) it.copy(engineIndex = 1, backgroundAlpha = .2f) else it
            }))
        }
        compose.waitUntil(5000) { applied == settings.hudSceneLayout }
        compose.runOnIdle { assertSame(original, native); assertEquals(1, count()) }
        val robot = java.awt.Robot()
        robot.waitForIdle()
        robot.delay(200)
        val output = java.io.File("build/hud-preview/single-window-regions.png")
        output.parentFile.mkdirs()
        javax.imageio.ImageIO.write(robot.createScreenCapture(original.bounds), "png", output)
        compose.setContent {}
        compose.runOnIdle { assertFalse(original.isDisplayable) }
    }

    @Test fun positionedRegionsHaveIndependentAlphaAndClearDelayedReadings() {
        val layout = HudSceneLayout(800, 400, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 300, 200, 0f),
            HudRegion("engine", HudRegionContent.ENGINE, 480, 180, 320, 220, 0.5f, engineIndex = 2)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = layout, hudFields = listOf("ias"), hudEngineFields = listOf("rpm")))
        var flight: ConnectionState by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 400.dp).background(Color.White)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        compose.onNodeWithText("2300 RPM").assertIsDisplayed()
        compose.onNodeWithText("发动机 #2").assertIsDisplayed()
        val first = compose.onNodeWithTag("hud-region-flight").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("hud-region-engine").fetchSemanticsNode().boundsInRoot
        assertTrue(second.left > first.right && second.top > first.top)
        fun background(id: String): Color {
            val pixels = compose.onNodeWithTag("hud-region-$id").captureToImage().toPixelMap()
            return pixels[pixels.width - 3, pixels.height - 3]
        }
        assertTrue(background("flight").red > 0.95f)
        assertTrue(background("engine").red in 0.50f..0.56f)
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = layout.copy(regions = layout.regions.map {
            if (it.id == "engine") it.copy(backgroundAlpha = 0f, contentAlpha = 0f, x = 400) else it
        })) }
        assertTrue(background("engine").red > 0.95f)
        compose.runOnIdle { flight = ConnectionState.Delayed }
        compose.onNodeWithText("340 km/h").assertDoesNotExist()
        compose.onNodeWithText("2300 RPM").assertDoesNotExist()
    }
}
