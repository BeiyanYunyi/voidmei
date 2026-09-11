package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*
import voidmei.telemetry.*

class RecordingNotificationsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualRecordingNotifiesAfterCloseAndDoesNotReplayWhenTrayReturns() {
        val root = Files.createTempDirectory("voidmei-recording-notifications")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val recorder = FlightRecorder(scope)
        val notices = mutableListOf<RecordingNotice>()
        var available by mutableStateOf(true)
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics())
        try {
            compose.setContent {
                val state by recorder.state.collectAsState()
                val saved by recorder.lastRecording.collectAsState()
                RecordingNotificationEffect(state, saved) { if (available) notices += it }
            }
            compose.waitForIdle()
            runBlocking { recorder.start(root) }
            compose.waitUntil(5000) { notices.size == 1 }
            compose.runOnIdle { assertEquals("已开启飞行记录", notices.single().title) }
            runBlocking { recorder.record(flight, 1000, 0) }
            compose.waitUntil(5000) { (recorder.state.value as? RecordingState.Active)?.samples == 1L }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, notices.size); assertNull(recorder.lastRecording.value) }
            runBlocking { recorder.stop() }
            compose.waitUntil(5000) { notices.size == 2 }
            val saved = assertNotNull(recorder.lastRecording.value)
            assertTrue(Files.isRegularFile(saved.flight) && Files.isRegularFile(saved.engines))
            compose.runOnIdle {
                assertEquals("飞行记录已保存", notices.last().title)
                assertTrue(notices.last().message.contains(saved.flight.toString()))
                assertTrue(notices.last().message.contains(saved.engines.toString()))
                available = false
            }
            runBlocking { recorder.start(root) }
            compose.waitForIdle()
            runBlocking { recorder.record(flight, 2000, 1000); recorder.stop() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(2, notices.size); available = true }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(2, notices.size) }
            runBlocking { recorder.start(root) }
            compose.waitUntil(5000) { notices.size == 3 }
            compose.runOnIdle { assertEquals("已开启飞行记录", notices.last().title) }
        } finally {
            runBlocking { recorder.close() }
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
