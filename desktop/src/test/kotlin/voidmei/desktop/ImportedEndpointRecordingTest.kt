package voidmei.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import voidmei.config.*
import voidmei.recording.*
import voidmei.telemetry.*

class ImportedEndpointRecordingTest {
    @Test fun importedPortReconnectSeparatesSameAircraftRecordingsAndResetsSep(): Unit = runBlocking {
        fun server(speed: Int) = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/state") { exchange ->
                val bytes = """{"valid":true,"IAS, km/h":$speed,"TAS, km/h":$speed,"Vy, m/s":0,"RPM 1":2000}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/indicators") { exchange ->
                val bytes = """{"valid":true,"type":"same-plane"}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val first = server(300)
        val second = server(600)
        val directory = Files.createTempDirectory("voidmei-imported-port-recording")
        val recorder = FlightRecorder(this)
        try {
            recorder.start(directory)
            val original = AppSettings(endpoint = "http://127.0.0.1:${first.address.port}")
            val imported = LegacySettingsReader.read("""(panel p (item port :type input :target "httpPort" :value ${second.address.port}))""")
            val updated = imported.applyTo(original)
            for (settings in listOf(original, updated)) {
                validateEndpoint(settings.endpoint)
                HttpTelemetryTransport(settings.endpoint).use { transport ->
                    var flying = 0
                    withTimeout(5000) {
                        TelemetryPoller(transport, intervalMs = 20).states().take(3).collect { state ->
                            recorder.record(state)
                            if (state is ConnectionState.Flying) {
                                if (flying++ == 0) assertNull(state.metrics.specificExcessPowerMps)
                                else assertEquals(0.0, state.metrics.specificExcessPowerMps)
                            }
                        }
                    }
                    assertEquals(2, flying)
                }
            }
            recorder.stop()
            val files = Files.list(directory).use { it.toList() }
            assertEquals(4, files.size)
            val speeds = files.filter { !it.fileName.toString().endsWith("-engines.csv") }.map { file ->
                val text = Files.readString(file)
                val replay = RecordedReplay(text)
                assertEquals(2, replay.size)
                assertNull(replay.frame(0).values["sep_mps"])
                assertEquals(0.0, replay.frame(1).values["sep_mps"])
                val range = FlightRecordReader.summarize(text).ranges.getValue("ias_kmh")
                assertEquals(range.minimum, range.maximum)
                val engineFile = file.resolveSibling(file.fileName.toString().removeSuffix(".csv") + "-engines.csv")
                assertEquals(2, EngineRecordTimeline.analyze(text, Files.readString(engineFile), 1).summary.samples)
                range.minimum
            }
            assertEquals(setOf(300.0, 600.0), speeds.toSet())
        } finally {
            recorder.close()
            first.stop(0); second.stop(0)
            Files.list(directory).use { it.forEach(Files::delete) }
            Files.delete(directory)
        }
    }
}
