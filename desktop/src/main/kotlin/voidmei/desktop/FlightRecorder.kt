package voidmei.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voidmei.telemetry.ConnectionState
import voidmei.recording.FlightCsv
import voidmei.recording.FlightPerformanceMonitor
import voidmei.recording.PerformanceObservation
import kotlinx.coroutines.channels.BufferOverflow
import java.io.BufferedWriter
import java.nio.file.*
import java.time.Instant
import java.util.UUID

sealed interface RecordingState {
    data object Stopped : RecordingState
    data class Active(val directory: String, val file: String? = null, val samples: Long = 0) : RecordingState
    data class Failed(val reason: String, val files: RecordedFiles? = null) : RecordingState
}

data class RecordedFiles(val flight: Path, val engines: Path)

/** Single IO worker; bounded queue applies backpressure instead of silently dropping telemetry. */
class FlightRecorder(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val openWriter: (Path) -> BufferedWriter = {
        Files.newBufferedWriter(it, Charsets.UTF_8, StandardOpenOption.CREATE_NEW)
    },
) {
    private sealed interface Command {
        data class Start(val directory: Path, val done: CompletableDeferred<Unit>) : Command
        data class Stop(val done: CompletableDeferred<Unit>) : Command
        data class Sample(val state: ConnectionState, val epochMs: Long, val monotonicMs: Long) : Command
    }
    private val commands = Channel<Command>(256)
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Stopped)
    val state: StateFlow<RecordingState> = mutableState.asStateFlow()
    private val mutableLastRecording = MutableStateFlow<RecordedFiles?>(null)
    val lastRecording: StateFlow<RecordedFiles?> = mutableLastRecording.asStateFlow()
    private val mutablePerformance = MutableSharedFlow<PerformanceObservation>(extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val performance: SharedFlow<PerformanceObservation> = mutablePerformance.asSharedFlow()
    @Volatile private var accepting = false
    @Volatile private var closed = false
    private val lifecycle = Mutex()
    private val worker = scope.launch(dispatcher) {
        var directory: Path? = null
        var aircraft: String? = null
        var flightWriter: BufferedWriter? = null
        var engineWriter: BufferedWriter? = null
        var file: Path? = null
        var affectedFiles: RecordedFiles? = null
        var sampleId = 0L
        var origin = 0L
        var performanceMonitor = FlightPerformanceMonitor()
        fun closeSegment() {
            val flight = flightWriter
            val engines = engineWriter
            val completedFile = file
            flightWriter = null; engineWriter = null; file = null; aircraft = null
            var failure: Exception? = null
            for (writer in listOfNotNull(flight, engines)) {
                try { writer.close() } catch (e: Exception) {
                    if (failure == null) failure = e else failure.addSuppressed(e)
                }
            }
            failure?.let { throw it }
            affectedFiles = null
            if (flight != null && engines != null && completedFile != null && sampleId > 0) {
                mutableLastRecording.value = RecordedFiles(completedFile,
                    completedFile.resolveSibling(completedFile.fileName.toString().removeSuffix(".csv") + "-engines.csv"))
            }
        }
        try {
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Start -> {
                            closeSegment()
                            Files.createDirectories(command.directory)
                            require(Files.isDirectory(command.directory) && Files.isWritable(command.directory)) { "Recording directory is not writable" }
                            directory = command.directory
                            // Once this command commits, reception belongs to the worker,
                            // even if the caller stopped waiting for its acknowledgement.
                            accepting = !closed
                            mutableState.value = RecordingState.Active(command.directory.toString())
                        }
                        is Command.Stop -> {
                            accepting = false
                            directory = null
                            closeSegment()
                            if (mutableState.value !is RecordingState.Failed) mutableState.value = RecordingState.Stopped
                        }
                        is Command.Sample -> {
                            val target = directory ?: continue
                            val flight = command.state as? ConnectionState.Flying
                            if (flight == null) {
                                closeSegment()
                                mutableState.value = RecordingState.Active(target.toString())
                                continue
                            }
                            if (flightWriter == null || aircraft != flight.telemetry.aircraft) {
                                closeSegment()
                                val stem = "flight-${Instant.ofEpochMilli(command.epochMs).toString().replace(':', '-')}-${UUID.randomUUID()}"
                                file = target.resolve("$stem.csv")
                                affectedFiles = RecordedFiles(file!!, target.resolve("$stem-engines.csv"))
                                sampleId = 0
                                flightWriter = openWriter(file!!)
                                engineWriter = openWriter(target.resolve("$stem-engines.csv"))
                                flightWriter!!.appendLine(FlightCsv.flightHeader)
                                engineWriter!!.appendLine(FlightCsv.engineHeader)
                                aircraft = flight.telemetry.aircraft
                                origin = command.monotonicMs
                                performanceMonitor = FlightPerformanceMonitor()
                                sampleId = 0
                            }
                            flightWriter!!.appendLine(FlightCsv.flightRow(sampleId, command.epochMs, command.monotonicMs - origin, flight))
                            FlightCsv.engineRows(sampleId, command.epochMs, flight.telemetry.engines).forEach { engineWriter!!.appendLine(it) }
                            flightWriter!!.flush(); engineWriter!!.flush()
                            performanceMonitor.update(flight, command.monotonicMs - origin).forEach { mutablePerformance.tryEmit(it) }
                            sampleId++
                            mutableState.value = RecordingState.Active(target.toString(), file.toString(), sampleId)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    accepting = false
                    directory = null
                    // A failed write may have left mismatched CSV rows; do not offer it as a completed pair.
                    sampleId = 0
                    val failedFiles = affectedFiles
                    runCatching { closeSegment() }
                    mutableState.value = RecordingState.Failed(e.message ?: "Cannot write flight log", failedFiles)
                    affectedFiles = null
                } finally {
                    when (command) {
                        is Command.Start -> command.done.complete(Unit)
                        is Command.Stop -> command.done.complete(Unit)
                        else -> Unit
                    }
                }
            }
        } finally {
            try { closeSegment() } catch (e: Exception) {
                if (mutableState.value !is RecordingState.Failed) {
                    mutableState.value = RecordingState.Failed(e.message ?: "Cannot close flight log", affectedFiles)
                }
            }
        }
    }
    init {
        // Also runs when the scope was cancelled before the worker entered its body.
        worker.invokeOnCompletion {
            accepting = false
            commands.cancel(CancellationException("Recording worker has stopped"))
            if (mutableState.value !is RecordingState.Failed) mutableState.value = RecordingState.Stopped
        }
    }

    private suspend fun awaitCommand(done: CompletableDeferred<Unit>) {
        select<Unit> {
            done.onAwait { }
            worker.onJoin { throw CancellationException("Recording worker stopped before completing the request") }
        }
    }

    suspend fun start(directory: Path) = lifecycle.withLock {
        check(!closed) { "Recording session is closed" }
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Start(directory, done))
        awaitCommand(done)
    }
    suspend fun record(state: ConnectionState, epochMs: Long = System.currentTimeMillis(), monotonicMs: Long = System.nanoTime() / 1_000_000) {
        if (accepting && !closed && this.state.value is RecordingState.Active) {
            try { commands.send(Command.Sample(state, epochMs, monotonicMs)) }
            catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                if (!closed) throw e
            }
        }
    }
    suspend fun stop() = lifecycle.withLock {
        if (closed) return@withLock
        accepting = false
        val done = CompletableDeferred<Unit>()
        try {
            commands.send(Command.Stop(done))
            awaitCommand(done)
        } catch (e: CancellationException) {
            // Cancellation while a full queue suspends send must not leave an active
            // recording muted. A Stop already delivered will disable reception itself.
            accepting = !closed && worker.isActive && state.value is RecordingState.Active
            throw e
        }
    }
    suspend fun close() = lifecycle.withLock {
        closed = true
        accepting = false
        commands.close()
        worker.join()
    }
}
