package voidmei.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.*
import java.io.BufferedWriter
import java.io.IOException
import java.io.StringWriter
import kotlin.test.*
import voidmei.telemetry.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

class FlightRecorderTest {
    @Test fun cancellingStopBeforeItCanEnterAFullQueueResumesReception(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-stop-full-queue")
        val tasks = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        val recorder = FlightRecorder(this, dispatcher)
        try {
            val starting = launch(start = CoroutineStart.UNDISPATCHED) { recorder.start(directory) }
            drain()
            starting.join()
            // One sample resumes the paused worker; 256 more fill its bounded queue.
            repeat(257) { recorder.record(flight(), 1000L + it, it.toLong()) }
            val stopping = launch(start = CoroutineStart.UNDISPATCHED) { recorder.stop() }
            assertFalse(stopping.isCompleted)
            stopping.cancelAndJoin()
            drain()
            assertEquals(257, assertIs<RecordingState.Active>(recorder.state.value).samples)
            recorder.record(flight(), 2000, 1000)
            drain()
            assertEquals(258, assertIs<RecordingState.Active>(recorder.state.value).samples)
            val committedStop = launch(start = CoroutineStart.UNDISPATCHED) { recorder.stop() }
            drain()
            committedStop.cancelAndJoin()
            assertIs<RecordingState.Stopped>(recorder.state.value)
            recorder.record(flight(), 3000, 2000)
            drain()
            val files = assertNotNull(recorder.lastRecording.value)
            assertEquals(259, Files.readAllLines(files.flight).size)
            assertEquals(259, Files.readAllLines(files.engines).size)
        } finally {
            val closing = launch(start = CoroutineStart.UNDISPATCHED) { recorder.close() }
            drain()
            closing.join()
            delete(directory)
        }
    }

    @Test fun committedStartKeepsRecordingWhenItsCallerIsCancelled(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-start-caller-cancel")
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val recorder = FlightRecorder(this, dispatcher)
        try {
            val request = launch(start = CoroutineStart.UNDISPATCHED) { recorder.start(directory) }
            request.cancelAndJoin()
            withTimeout(5000) { recorder.state.first { it is RecordingState.Active } }
            recorder.record(flight(), 1000, 0)
            recorder.stop()
            val files = assertNotNull(recorder.lastRecording.value)
            assertEquals(2, Files.readAllLines(files.flight).size)
            assertEquals(2, Files.readAllLines(files.engines).size)
        } finally { recorder.close(); delete(directory) }
    }

    @Test fun exitCheckReportsStoppedWorkerWithoutCancellingTheCaller(): Unit = runBlocking {
        val owner = SupervisorJob()
        val recorder = FlightRecorder(CoroutineScope(coroutineContext + owner))
        try {
            owner.cancelAndJoin()
            val failure = withTimeout(5000) { stopRecordingForExit(recorder) }
            assertNotNull(failure)
            assertTrue(failure.isNotBlank())
            assertTrue(currentCoroutineContext().isActive)
            withTimeout(5000) { recorder.close() }
        } finally { owner.cancel(); recorder.close() }
    }

    @Test fun exitCheckStillPropagatesActualCallerCancellation(): Unit = runBlocking {
        val recorder = FlightRecorder(this)
        try {
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                assertFailsWith<CancellationException> { stopRecordingForExit(recorder) }
            }
            caller.join()
            assertTrue(caller.isCancelled)
        } finally { recorder.close() }
    }

    @Test fun exitCheckStopsRecordingAndReportsFlushFailure(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-exit-record")
        val opened = mutableListOf<Path>()
        val recorder = FlightRecorder(this, openWriter = { path ->
            opened.add(path)
            object : BufferedWriter(StringWriter()) {
                override fun close() { throw IOException("Disk full at close") }
            }
        })
        try {
            assertNull(stopRecordingForExit(recorder))
            recorder.start(directory)
            recorder.record(flight(), 1000, 0)
            val failure = assertNotNull(stopRecordingForExit(recorder))
            assertTrue(failure.startsWith("Disk full at close"))
            val files = assertNotNull(assertIs<RecordingState.Failed>(recorder.state.value).files)
            assertEquals(opened, listOf(files.flight, files.engines))
            assertTrue(failure.contains(files.flight.toString()))
            assertTrue(failure.contains(files.engines.toString()))
            assertEquals(failure, stopRecordingForExit(recorder))
            assertNull(recorder.lastRecording.value)
        } finally { recorder.close(); delete(directory) }
    }

    private fun flight(name: String = "test") = ConnectionState.Flying(TelemetryParser.parse(
        """{"valid":true,"IAS, km/h":100,"RPM 1":2000}""", """{"valid":true,"type":"$name"}""")!!, FlightMetrics())

    @Test fun recordsIdenticalFramesAndDrainsAllRowsOnStop(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record")
        val recorder = FlightRecorder(this)
        try {
            recorder.start(directory)
            assertNull(recorder.lastRecording.value)
            repeat(300) { recorder.record(flight(), 1000L + it, it.toLong()) }
            recorder.stop()
            Files.list(directory).use { paths ->
                val files = paths.toList()
                assertEquals(2, files.size)
                val recent = assertNotNull(recorder.lastRecording.value)
                assertEquals(files.toSet(), setOf(recent.flight, recent.engines))
                files.forEach { assertEquals(301, Files.readAllLines(it).size) }
            }
            assertIs<RecordingState.Stopped>(recorder.state.value)
        } finally { recorder.close(); delete(directory) }
    }

    @Test fun aircraftAndDisconnectBoundariesCreateSeparateFiles(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-segments")
        val recorder = FlightRecorder(this)
        try {
            recorder.start(directory)
            recorder.record(flight("first"), 1000, 10)
            recorder.record(flight("second"), 1100, 20)
            recorder.record(ConnectionState.Disconnected("offline"), 1200, 30)
            recorder.record(flight("second"), 1300, 40)
            recorder.close()
            assertIs<RecordingState.Stopped>(recorder.state.value)
            Files.list(directory).use { paths ->
                val files = paths.toList()
                assertEquals(6, files.size)
                files.forEach { assertEquals(2, Files.readAllLines(it).size) }
            }
        } finally { recorder.close(); delete(directory) }
    }

    @Test fun invalidDirectoryReportsFailureAndCanRestart(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-failure")
        val file = Files.writeString(directory.resolve("not-a-directory"), "existing")
        val recorder = FlightRecorder(this)
        try {
            recorder.start(file)
            assertIs<RecordingState.Failed>(recorder.state.value)
            assertEquals("existing", Files.readString(file))
            recorder.start(directory.resolve("logs"))
            assertIs<RecordingState.Active>(recorder.state.value)
            recorder.stop()
        } finally { recorder.close(); delete(directory) }
    }

    @Test fun cancelledWorkerReleasesAQueuedStart(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-cancel")
        val owner = SupervisorJob()
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val recorder = FlightRecorder(CoroutineScope(coroutineContext + owner), dispatcher)
        try {
            val start = async(start = CoroutineStart.UNDISPATCHED) { recorder.start(directory) }
            owner.cancelAndJoin()
            withTimeout(5000) { assertFailsWith<CancellationException> { start.await() } }
            assertIs<RecordingState.Stopped>(recorder.state.value)
            withTimeout(5000) { recorder.close(); recorder.stop() }
            Files.list(directory).use { assertEquals(0, it.count()) }
        } finally { owner.cancel(); recorder.close(); delete(directory) }
    }

    @Test fun cancelledWorkerReleasesAQueuedStop(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-cancel-stop")
        val owner = SupervisorJob()
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val recorder = FlightRecorder(CoroutineScope(coroutineContext + owner), dispatcher)
        try {
            recorder.start(directory)
            val stop = async(start = CoroutineStart.UNDISPATCHED) { recorder.stop() }
            owner.cancelAndJoin()
            withTimeout(5000) { assertFailsWith<CancellationException> { stop.await() } }
            assertIs<RecordingState.Stopped>(recorder.state.value)
        } finally { owner.cancel(); recorder.close(); delete(directory) }
    }

    @Test fun overlappingStartStopAndCloseCannotReenableAClosedRecorder(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-lifecycle")
        val recorder = FlightRecorder(this, coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val start = async(start = CoroutineStart.UNDISPATCHED) { recorder.start(directory) }
            val stop = async(start = CoroutineStart.UNDISPATCHED) { recorder.stop() }
            val close = async(start = CoroutineStart.UNDISPATCHED) { recorder.close() }
            withTimeout(5000) { start.await(); stop.await(); close.await() }
            assertIs<RecordingState.Stopped>(recorder.state.value)
            recorder.record(flight())
            recorder.stop()
            recorder.close()
            assertFailsWith<IllegalStateException> { recorder.start(directory) }
            Files.list(directory).use { assertEquals(0, it.count()) }
        } finally { recorder.close(); delete(directory) }
    }

    private fun delete(directory: Path) {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    @Test fun finalCloseReportsFirstFailureAndAttemptsBothWriters(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-close-failure")
        val closed = mutableListOf<String>()
        val recorder = FlightRecorder(this, openWriter = { path ->
            val kind = if (path.fileName.toString().endsWith("-engines.csv")) "engines" else "flight"
            object : BufferedWriter(StringWriter()) {
                override fun close() {
                    closed.add(kind)
                    throw IOException("Cannot close $kind")
                }
            }
        })
        try {
            recorder.start(directory)
            recorder.record(flight(), 1000, 0)
            recorder.close()
            assertEquals(listOf("flight", "engines"), closed)
            assertEquals("Cannot close flight", assertIs<RecordingState.Failed>(recorder.state.value).reason)
            assertNull(recorder.lastRecording.value)
            recorder.stop()
            recorder.close()
            assertIs<RecordingState.Failed>(recorder.state.value)
            assertEquals(2, closed.size)
        } finally { recorder.close(); delete(directory) }
    }

    @Test fun writeFailureSurvivesStopAndSuccessfulRestartClearsIt(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-record-write-failure")
        var fail = true
        var closed = 0
        val recorder = FlightRecorder(this, openWriter = {
            object : BufferedWriter(StringWriter()) {
                override fun flush() {
                    if (fail) throw IOException("Disk write failed")
                    super.flush()
                }
                override fun close() {
                    closed++
                    if (fail) throw IOException("Secondary close failure")
                    super.close()
                }
            }
        })
        try {
            recorder.start(directory)
            recorder.record(flight(), 1000, 0)
            recorder.stop()
            assertEquals("Disk write failed", assertIs<RecordingState.Failed>(recorder.state.value).reason)
            assertNull(recorder.lastRecording.value)
            assertEquals(2, closed)
            fail = false
            recorder.start(directory)
            assertIs<RecordingState.Active>(recorder.state.value)
            recorder.record(flight(), 2000, 1000)
            recorder.stop()
            assertIs<RecordingState.Stopped>(recorder.state.value)
            assertEquals(4, closed)
            val completed = assertNotNull(recorder.lastRecording.value)
            fail = true
            recorder.start(directory)
            recorder.record(flight(), 3000, 2000)
            recorder.stop()
            val failed = assertNotNull(assertIs<RecordingState.Failed>(recorder.state.value).files)
            assertNotEquals(completed, failed)
            assertEquals(completed, recorder.lastRecording.value)
            assertEquals(6, closed)
        } finally { recorder.close(); delete(directory) }
    }
}
