package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class RecordingFailureGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failureRestoresOnceWithoutReactingToNormalSamplesOrRecomposition() {
        var state by mutableStateOf<RecordingState>(RecordingState.Stopped)
        var error by mutableStateOf<String?>(null)
        var callbackVersion by mutableStateOf(0)
        val restores = mutableListOf<Int>()
        compose.setContent {
            val version = callbackVersion
            RecordingFailureEffect(state, error) { restores += version }
        }
        compose.runOnIdle { state = RecordingState.Active("records") }
        compose.runOnIdle { state = RecordingState.Active("records", "flight.csv", 100) }
        compose.runOnIdle { assertTrue(restores.isEmpty()); state = RecordingState.Failed("disk full") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0), restores); callbackVersion = 1 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0), restores); state = RecordingState.Failed("disk full") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0), restores); state = RecordingState.Active("new") }
        compose.waitForIdle()
        compose.runOnIdle { state = RecordingState.Failed("disk full") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0, 1), restores); state = RecordingState.Stopped }
        compose.waitForIdle()
        compose.runOnIdle { error = "invalid path" }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0, 1, 1), restores) }
    }

    @Test fun realRecorderStartupFailureRestoresHiddenStateAndRecoveryDoesNot() {
        val root = Files.createTempDirectory("voidmei-recording-attention")
        val blocked = Files.writeString(root.resolve("blocked"), "a file is not a recording directory")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val recorder = FlightRecorder(scope)
        var visible by mutableStateOf(false)
        var restores = 0
        try {
            compose.setContent {
                val state by recorder.state.collectAsState()
                RecordingFailureEffect(state, null) { visible = true; restores++ }
            }
            runBlocking { recorder.start(blocked) }
            compose.waitUntil(5000) { visible }
            compose.runOnIdle { assertIs<RecordingState.Failed>(recorder.state.value); assertEquals(1, restores) }
            runBlocking { recorder.start(root.resolve("valid")) }
            compose.waitForIdle()
            compose.runOnIdle { visible = false }
            runBlocking { recorder.stop() }
            compose.waitForIdle()
            compose.runOnIdle { assertFalse(visible); assertEquals(1, restores) }
            runBlocking { recorder.start(blocked) }
            compose.waitUntil(5000) { visible }
            compose.runOnIdle { assertEquals(2, restores) }
        } finally {
            runBlocking { recorder.close() }
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
