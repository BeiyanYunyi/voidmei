package voidmei.desktop

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import voidmei.config.AppSettings
import kotlin.test.*

class RecordingStartupTest {
    @Test fun configuredStartupWaitsForFlightAndReportsInvalidDirectory(): Unit = runBlocking {
        val root = Files.createTempDirectory("voidmei-auto-recording")
        val target = root.resolve("records")
        val blocked = root.resolve("file")
        val recorder = FlightRecorder(this)
        try {
            val settings = AppSettings(recordingDirectory = target.toString())
            startConfiguredRecording(recorder, settings)
            assertIs<RecordingState.Stopped>(recorder.state.value)
            assertFalse(Files.exists(target))
            startConfiguredRecording(recorder, settings.copy(recordingAutoStart = true))
            val active = assertIs<RecordingState.Active>(recorder.state.value)
            assertNull(active.file)
            Files.list(target).use { assertEquals(0, it.count()) }
            recorder.stop()
            assertIs<RecordingState.Stopped>(recorder.state.value)
            Files.writeString(blocked, "unchanged")
            startConfiguredRecording(recorder, settings.copy(recordingAutoStart = true, recordingDirectory = blocked.toString()))
            assertIs<RecordingState.Failed>(recorder.state.value)
            assertEquals("unchanged", Files.readString(blocked))
        } finally {
            recorder.close()
            Files.deleteIfExists(blocked); Files.deleteIfExists(target); Files.delete(root)
        }
    }
}
