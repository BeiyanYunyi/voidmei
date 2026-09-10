package voidmei.desktop

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import voidmei.recording.*
import voidmei.telemetry.*

class RecordExportTest {
    @Test fun croppedTemperatureRecordSurvivesFileExportAndReload() = withDirectory { directory ->
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"测试,机型"}""")!!
        val values = listOf(100.0, null, 0.0, -20.0)
        val source = FlightCsv.flightHeader + ",future_note\n" + values.mapIndexed { index, temperature ->
            val flight = ConnectionState.Flying(base.copy(waterTemperatureRaw = temperature,
                headTemperatureRaw = temperature?.plus(10), oilTemperatureRaw = temperature?.minus(5)), FlightMetrics())
            FlightCsv.flightRow(10L + index, 1_000_000L + index * 1000, 5000L + index * 1000, flight) +
                ",\"保留,第 $index 条\n第二行\""
        }.joinToString("\n")
        val sourceFile = directory.resolve("original.csv")
        Files.writeString(sourceFile, source)
        val window = FlightRecordWindow.analyze(Files.readString(sourceFile), 1000, 2000)
        assertEquals(1000L, window.firstPointOffsetMs)
        val target = directory.resolve("selection.csv")
        exportRecordCsv(target, window.text)
        val exported = Files.readString(target)
        assertEquals(window.text, exported)
        assertEquals(source, Files.readString(sourceFile))
        assertContains(exported, "future_note")
        assertContains(exported, "保留,第 1 条\n第二行")
        assertContains(exported, "保留,第 2 条\n第二行")
        assertFalse(exported.contains("保留,第 0 条"))
        val replay = RecordedReplay(exported)
        assertEquals(2, replay.size)
        assertEquals(1000L, replay.spanMs)
        assertEquals(11L, replay.frame(0).sampleId)
        assertEquals(12L, replay.frame(1).sampleId)
        assertEquals(1_001_000L, replay.frame(0).epochMs)
        assertEquals("测试,机型", replay.frame(1).aircraft)
        assertNull(replay.frame(0).values["water_temperature_raw"])
        assertEquals(0.0, replay.frame(1).values["water_temperature_raw"])
        assertEquals(10.0, replay.frame(1).values["head_temperature_raw"])
        assertEquals(-5.0, replay.frame(1).values["oil_temperature_raw"])
        val analysis = FlightRecordPlots.analyze(exported)
        assertEquals(RecordedRange(1, 0.0, 0.0), analysis.summary.ranges["water_temperature_raw"])
        assertFalse(analysis.plots.getValue("water_temperature_raw").single().connectFromPrevious)
        assertFailsWith<FileAlreadyExistsException> { exportRecordCsv(target, source) }
        assertEquals(exported, Files.readString(target))
        Files.list(directory).use { assertEquals(2, it.count()) }
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("voidmei-export")
        try { test(directory) }
        finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }

    @Test fun successfulExportPublishesCompleteUtf8WithoutTemporaryFiles() = withDirectory { directory ->
        val target = directory.resolve("flight.csv")
        val text = "sample_id,aircraft\n1,测试\n"
        exportRecordCsv(target, text)
        assertEquals(text, Files.readString(target))
        Files.list(directory).use { assertEquals(listOf(target), it.toList()) }
    }

    @Test fun interruptedWriteLeavesNoDestinationOrTemporaryFile() = withDirectory { directory ->
        val target = directory.resolve("flight.csv")
        assertFailsWith<IOException> {
            exportRecordCsv(target, "complete record") { temporary, _ ->
                Files.writeString(temporary, "partial")
                throw IOException("simulated disk error")
            }
        }
        assertFalse(Files.exists(target))
        Files.list(directory).use { assertEquals(0, it.count()) }
    }

    @Test fun concurrentlyCreatedDestinationIsNotReplaced() = withDirectory { directory ->
        val target = directory.resolve("flight.csv")
        assertFailsWith<FileAlreadyExistsException> {
            exportRecordCsv(target, "new record") { temporary, bytes ->
                Files.write(temporary, bytes)
                Files.writeString(target, "another writer")
            }
        }
        assertEquals("another writer", Files.readString(target))
        Files.list(directory).use { assertEquals(listOf(target), it.toList()) }
    }
}
