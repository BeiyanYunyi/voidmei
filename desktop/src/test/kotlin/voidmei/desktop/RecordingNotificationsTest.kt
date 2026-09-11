package voidmei.desktop

import kotlin.test.*
import java.nio.file.Path
import java.awt.EventQueue
import java.io.BufferedWriter
import java.io.StringWriter
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import voidmei.telemetry.*

class RecordingNotificationsTest {
    private fun files(name: String) = RecordedFiles(Path.of("$name.csv"), Path.of("$name-engines.csv"))

    @Test fun ignoresHistoricalFilesAndSamplesAndReportsEachNewSavedSegment() {
        val notices = RecordingNotifications()
        val history = files("old")
        assertTrue(notices.update(RecordingState.Stopped, history).isEmpty())
        assertEquals("已开启飞行记录", notices.update(RecordingState.Active("records"), history).single().title)
        assertTrue(notices.update(RecordingState.Active("records", "flight.csv", 10), history).isEmpty())
        val saved = files("first")
        val message = notices.update(RecordingState.Active("records"), saved).single()
        assertEquals("飞行记录已保存", message.title)
        assertTrue(message.message.contains("first-engines.csv"))
        assertTrue(notices.update(RecordingState.Stopped, saved).isEmpty())
        assertTrue(notices.update(RecordingState.Failed("disk full"), saved).isEmpty())
        assertEquals("已开启飞行记录", notices.update(RecordingState.Active("records"), saved).single().title)
        assertEquals("飞行记录已保存", notices.update(RecordingState.Stopped, files("second")).single().title)
    }

    @Test fun failedCloseDoesNotPublishASuccessfulSave() = runBlocking {
        val root = Files.createTempDirectory("voidmei-notification-close")
        val recorder = FlightRecorder(this, openWriter = {
            object : BufferedWriter(StringWriter()) {
                override fun close() { throw IOException("close failed") }
            }
        })
        val notices = RecordingNotifications()
        try {
            notices.update(recorder.state.value, recorder.lastRecording.value)
            recorder.start(root)
            notices.update(recorder.state.value, recorder.lastRecording.value)
            val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
            recorder.record(ConnectionState.Flying(telemetry, FlightMetrics()), 1000, 0)
            recorder.stop()
            assertIs<RecordingState.Failed>(recorder.state.value)
            assertNull(recorder.lastRecording.value)
            assertTrue(notices.update(recorder.state.value, recorder.lastRecording.value).isEmpty())
        } finally { recorder.close(); root.toFile().deleteRecursively() }
    }

    @Test fun trayDeliveryUsesAwtAndDropsPendingMessagesOnCloseOrTrayLoss() {
        val delivered = mutableListOf<String>()
        var available = true
        val messages = TrayMessages({ title, _ -> assertTrue(EventQueue.isDispatchThread()); delivered += title }, { available })
        messages.show("first", "body")
        EventQueue.invokeAndWait { assertEquals(listOf("first"), delivered) }
        EventQueue.invokeAndWait { messages.show("lost", "body"); available = false }
        EventQueue.invokeAndWait { assertEquals(listOf("first"), delivered); available = true }
        EventQueue.invokeAndWait { messages.show("closed", "body"); messages.close() }
        messages.show("after-close", "body")
        EventQueue.invokeAndWait { assertEquals(listOf("first"), delivered) }
    }
}
