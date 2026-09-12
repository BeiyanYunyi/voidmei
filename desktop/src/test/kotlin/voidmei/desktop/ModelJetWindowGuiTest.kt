package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.fm.JetThrustModel
import kotlin.test.*

class ModelJetWindowGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun contentShowsAllSourcesAndCloseAction() {
        val jet = JetThrustModel("Engine0", listOf(0.0), listOf(0.0, 1000.0), listOf(listOf(1000.0, 2000.0)), null)
        var closed = false
        compose.setContent { androidx.compose.material3.MaterialTheme {
            ModelJetWindowContent(listOf(jet, jet.copy(source = "Engine1"))) { closed = true }
        } }
        compose.onAllNodesWithTag("jet-altitudes-plot").assertCountEquals(2)
        compose.onNodeWithText("多高度推力–真空速 · Engine0 · kgf/台").assertExists()
        compose.onNodeWithText("多高度推力–真空速 · Engine1 · kgf/台").assertExists()
        compose.onNodeWithTag("model-jet-window-close").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun automaticDismissalClosesNativeWindowWithoutChangingSavedPreferences() {
        val jet = JetThrustModel("Engine0", listOf(0.0), listOf(0.0), listOf(listOf(1000.0)), null)
        val telemetry = voidmei.telemetry.TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!.copy(gearPercent = 0.0)
        var settings by mutableStateOf(AppSettings(modelWindowEnabled = true, modelJetWindowEnabled = true, modelJetWindowAutoClose = true))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModelJetWindow(rememberWindowState(width = 820.dp, height = 700.dp), settings, listOf(jet), telemetry, "A", onClose = {})
        }
        fun frames() = Frame.getFrames().filter { it.title == "VoidMei · 喷气推力" && it.isDisplayable }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5000) { frames().singleOrNull()?.isVisible == true }
        compose.mainClock.advanceTimeBy(10200)
        compose.waitUntil(5000) { frames().isEmpty() }
        compose.runOnIdle { assertTrue(settings.modelJetWindowEnabled); assertTrue(settings.modelWindowEnabled); settings = settings.copy(modelWindowHotkeyEnabled = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5000) { frames().singleOrNull()?.isVisible == true }
        compose.mainClock.advanceTimeBy(12000)
        assertEquals(1, frames().size)
    }
    @Test fun linkedWindowRequiresBothSwitchesAndJetDataAndClosesIndependently() {
        val jet = JetThrustModel("Engine0", listOf(0.0), listOf(0.0, 1000.0), listOf(listOf(1000.0, 2000.0)), null)
        var models by mutableStateOf(listOf(jet))
        var settings by mutableStateOf(AppSettings(modelJetWindowEnabled = true))
        compose.setContent {
            ModelJetWindow(rememberWindowState(width = 820.dp, height = 700.dp), settings, models,
                onClose = { settings = settings.copy(modelJetWindowEnabled = false) })
        }
        fun frames() = Frame.getFrames().filter { it.title == "VoidMei · 喷气推力" && it.isDisplayable }
        assertTrue(frames().isEmpty())
        compose.runOnIdle { settings = settings.copy(modelWindowEnabled = true) }
        compose.waitUntil(5000) { frames().singleOrNull()?.isVisible == true }
        assertTrue(frames().single().isAlwaysOnTop)
        compose.runOnIdle { settings = settings.copy(modelWindowEnabled = false) }
        compose.waitUntil(5000) { frames().isEmpty() }
        compose.runOnIdle { settings = settings.copy(modelWindowEnabled = true, modelWindowAlwaysOnTop = false) }
        compose.waitUntil(5000) { frames().singleOrNull()?.isVisible == true }
        assertFalse(frames().single().isAlwaysOnTop)
        val closingFrame = frames().single()
        java.awt.EventQueue.invokeAndWait { closingFrame.dispatchEvent(java.awt.event.WindowEvent(closingFrame, java.awt.event.WindowEvent.WINDOW_CLOSING)) }
        compose.waitUntil(5000) { frames().isEmpty() }
        compose.runOnIdle { assertTrue(settings.modelWindowEnabled); assertFalse(settings.modelJetWindowEnabled) }
        compose.runOnIdle { models = emptyList(); settings = settings.copy(modelJetWindowEnabled = true) }
        compose.runOnIdle { assertTrue(frames().isEmpty()) }
        compose.runOnIdle { models = listOf(jet, jet.copy(source = "Engine1")) }
        compose.waitUntil(5000) { frames().singleOrNull()?.isVisible == true }
        compose.runOnIdle { models = emptyList() }
        compose.waitUntil(5000) { frames().isEmpty() }
    }
}
