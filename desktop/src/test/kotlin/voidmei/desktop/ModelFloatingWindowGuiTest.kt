package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.fm.FlightModelExtractor
import voidmei.telemetry.TelemetryParser
import kotlin.test.*

class ModelFloatingWindowGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativeWindowUsesSharedSessionChangesTopmostAndClosesIndependently() {
        val root = Files.createTempDirectory("voidmei-model-window")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), "fmFile:t=\"fm/test.blk\"")
        Files.writeString(directory.resolve("fm/test.blkx"), "Mass { EmptyMass:r=2500 }")
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        var settings by mutableStateOf(AppSettings(fmDataRoot = root.toString(), modelWindowEnabled = true))
        var aircraft by mutableStateOf<String?>("test")
        val extracts = AtomicInteger()
        var session: FlightModelSession? = null
        try {
            compose.setContent {
                val current = rememberFlightModelSession(aircraft, root.toString()) { document, fuel ->
                    extracts.incrementAndGet(); FlightModelExtractor.extract(document, fuel)
                }
                SideEffect { session = current }
                if (settings.modelWindowEnabled) ModelFloatingWindow(rememberWindowState(width = 760.dp, height = 680.dp),
                    settings, telemetry.takeIf { aircraft != null }, current,
                    onClose = { settings = settings.copy(modelWindowEnabled = false) }, onChange = { settings = it })
            }
            compose.waitUntil(10000) { session?.alertModel != null }
            compose.waitUntil(5000) { compose.onAllNodesWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).assertExists()
            val native = Frame.getFrames().single { it.title == "VoidMei · 当前模型" && it.isDisplayable }
            assertTrue(native.isAlwaysOnTop)
            compose.runOnIdle { settings = settings.copy(modelWindowAlwaysOnTop = false) }
            compose.waitUntil(5000) { !native.isAlwaysOnTop }
            val before = extracts.get()
            compose.onNodeWithTag("model-window-close", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5000) { !native.isDisplayable }
            compose.runOnIdle { assertNotNull(session?.alertModel); settings = settings.copy(modelWindowEnabled = true) }
            compose.waitUntil(5000) { compose.onAllNodesWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).assertExists()
            assertEquals(before, extracts.get())
            compose.runOnIdle { aircraft = null }
            compose.waitUntil(5000) { session?.alertModel == null }
            compose.waitUntil(5000) { compose.onAllNodesWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true, useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("进入飞行后自动加载当前机型。", useUnmergedTree = true).assertExists()
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun controlsDoNotChangeHudOrModelCategories() {
        var settings by mutableStateOf(AppSettings(hiddenModelSections = setOf(ModelDetailSection.RAW)))
        val initial = settings
        compose.setContent { MaterialTheme { Column { ModelWindowControls(settings) { settings = it } } } }
        compose.onNodeWithTag("model-window-enabled").performClick()
        compose.onNodeWithTag("model-window-on-top").performClick()
        compose.onNodeWithTag("model-window-hotkey").performClick()
        compose.runOnIdle { assertEquals(initial.copy(modelWindowHotkeyEnabled = true, modelWindowEnabled = true, modelWindowAlwaysOnTop = false), settings) }
    }
}
