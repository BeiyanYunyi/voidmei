package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import voidmei.recording.*
import kotlin.test.*

class RecordExportGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportsSelectedOriginalRowsAndRefusesToReplaceExistingFile() {
        val directory = Files.createTempDirectory("voidmei-export-gui")
        val output = directory.resolve("区间.csv")
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n10,1000,500,test,300\n11,2000,1500,test,400\n"
        var selected: String? = null
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                RecordingWindowPanel(text, FlightRecordPlots.analyze(text), chooseExport = { selected })
            } } }
            compose.onNodeWithText("导出当前区间 CSV").performScrollTo().performClick()
            assertFalse(Files.exists(output))
            compose.onNodeWithText("区间起点 (s)").performScrollTo().performTextReplacement("1")
            compose.onNodeWithText("区间终点 (s)").performTextReplacement("1")
            compose.onNodeWithText("分析此区间").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("当前曲线：1.0–1.0 s · 1 帧").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { selected = output.toString() }
            compose.onNodeWithText("导出当前区间 CSV").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已导出：$output").fetchSemanticsNodes().isNotEmpty() }
            val exported = Files.readString(output)
            val frame = RecordedReplay(exported).frame(0)
            assertEquals(11L, frame.sampleId)
            assertEquals(2000L, frame.epochMs)
            assertEquals(400.0, frame.values["ias_kmh"])
            assertEquals(1, FlightRecordReader.summarize(exported).samples)
            compose.onNodeWithText("导出当前区间 CSV").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("目标文件已存在，请选择新文件名。").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(exported, Files.readString(output))
        } finally { Files.deleteIfExists(output); Files.delete(directory) }
    }
}
