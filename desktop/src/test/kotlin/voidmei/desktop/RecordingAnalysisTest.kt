package voidmei.desktop

import java.nio.file.Files
import kotlin.test.*

class RecordingAnalysisTest {
    @Test fun pairedFlightSuggestionUsesOnlyTheRecorderSuffix() {
        val directory = java.nio.file.Path.of("records", "带 空格")
        assertEquals(directory.resolve("flight-test.csv").toString(), suggestedFlightPath(directory.resolve("flight-test-engines.csv").toString()))
        assertEquals("flight.csv", suggestedFlightPath("flight-ENGINES.CSV"))
        for (name in listOf("engines.csv", "-engines.csv", "flight.csv", "flight-engines.csv.bak", ""))
            assertNull(suggestedFlightPath(name))
    }

    @Test fun directoriesAndMissingPathsAreRejectedBeforeOpeningAStream() {
        val directory = Files.createTempDirectory("voidmei-record-path")
        try {
            assertEquals("请选择普通 CSV 文件", assertFailsWith<IllegalArgumentException> { readFlightText(directory) }.message)
            assertFailsWith<IllegalArgumentException> { readFlightText(directory.resolve("missing.csv")) }
        } finally { Files.delete(directory) }
    }

    @Test fun legacyImportRequiresExplicitFormatAndLeavesSourceUnchanged() {
        val file = Files.createTempFile("voidmei-legacy-summary", ".csv")
        val row = MutableList(32) { "0" }.apply { this[0] = "1"; this[2] = "300"; this[31] = "" }.joinToString(",")
        val text = voidmei.recording.LegacyFlightRecordReader.header + "\n" + row + "\n"
        try {
            Files.writeString(file, text)
            assertFails { readFlightAnalysis(file) }
            val result = readFlightAnalysis(file, legacy = true)
            assertEquals(300.0, result.summary.ranges["ias_kmh"]?.maximum)
            assertNull(result.summary.startEpochMs)
            assertEquals(text, Files.readString(file))
            Files.write(file, text.toByteArray(java.nio.charset.Charset.forName("GB18030")))
            assertFails { readFlightAnalysis(file, legacy = true) }
            assertEquals(300.0, readFlightAnalysis(file, legacy = true, gb18030 = true).summary.ranges["ias_kmh"]?.maximum)
        } finally { Files.delete(file) }
    }
    @Test fun readsUtf8FileAndRejectsBrokenEncoding() {
        val file = Files.createTempFile("voidmei-summary", ".csv")
        try {
            Files.writeString(file, "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,测试,300\n")
            assertEquals("测试", readFlightSummary(file).aircraft)
            Files.write(file, byteArrayOf(0xC3.toByte(), 0x28))
            assertFails { readFlightSummary(file) }
        } finally { Files.delete(file) }
    }
}
