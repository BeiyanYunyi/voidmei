package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class ComposeTrayRestoreGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedTrayRequestsRestoreEvenWhenHiddenWindowCompositionIsPaused() {
        var visible by mutableStateOf(false)
        var request by mutableStateOf(0L)
        var native: Frame? = null
        compose.setContent {
            RestorableDesktopWindow({}, "Compose tray restore test",
                rememberWindowState(width = 320.dp, height = 180.dp), visible, request) {
                SideEffect { native = window }
                Text("Tray restore")
            }
        }
        compose.runOnIdle { visible = true; request++ }
        compose.waitUntil(5000) { native?.isVisible == true }
        val frame = assertNotNull(native)
        // Normal hide-to-tray cycle.
        compose.runOnIdle { visible = false }
        compose.waitUntil(5000) { !frame.isVisible }
        compose.runOnIdle { visible = true; request++ }
        compose.waitUntil(5000) { frame.isVisible }
        // Native visibility can diverge from Compose state; repeated restore still must run.
        repeat(2) {
            compose.runOnIdle { frame.isVisible = false }
            compose.waitUntil(5000) { !frame.isVisible }
            compose.runOnIdle { request++ }
            compose.waitUntil(5000) { frame.isVisible }
        }
        assertSame(frame, native)
    }
}
