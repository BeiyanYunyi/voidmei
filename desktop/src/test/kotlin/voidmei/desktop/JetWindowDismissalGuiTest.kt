package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class JetWindowDismissalGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun triggerLatchesForTenSecondsAndNewSessionRestores() {
        var trigger by mutableStateOf(false)
        var session by mutableStateOf("A")
        compose.mainClock.autoAdvance = false
        compose.setContent { Text("dismissed=" + rememberJetWindowDismissed(true, true, trigger, session)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { trigger = true }
        compose.mainClock.advanceTimeBy(9000)
        compose.onNodeWithText("dismissed=false").assertExists()
        compose.runOnIdle { trigger = false }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("dismissed=true").assertExists()
        compose.runOnIdle { session = "B" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("dismissed=false").assertExists()
    }
    @Test fun telemetryLossAndDisablingCancelPendingTimer() {
        var live by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { Text("dismissed=" + rememberJetWindowDismissed(enabled, live, true, "A")) }
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { live = false }
        compose.mainClock.advanceTimeBy(12000)
        compose.onNodeWithText("dismissed=false").assertExists()
        compose.runOnIdle { live = true }
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithText("dismissed=false").assertExists()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(12000)
        compose.onNodeWithText("dismissed=false").assertExists()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(10200)
        compose.onNodeWithText("dismissed=true").assertExists()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("dismissed=false").assertExists()
    }
}
