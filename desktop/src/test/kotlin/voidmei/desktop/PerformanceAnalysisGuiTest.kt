package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class PerformanceAnalysisGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun analyzesSelectsAndExportsWithoutReplacingExistingFiles() {
        val root = Files.createTempDirectory("voidmei-performance-export")
        val target = root.resolve("result.csv")
        val source = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,altitude_m,ias_kmh,roll_rate_degps,aileron_percent\n" +
            "0,,0,test,100,200,100,80\n1,,5000,test,200,220,150,90\n"
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                PerformanceAnalysisPanel(source) { target.toString() }
            } } }
            compose.onNodeWithText("统计爬升与机动采样").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("高度档 2 · 滚转速度档 2 · 过载速度档 0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("performance-plot").assertExists()
            compose.onNodeWithText("滚转率", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("所选采样：200.00 km/h · 100.00 °/s").assertExists()
            compose.onNodeWithText("平滑过载", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("此项没有有效采样").assertExists()
            compose.onNodeWithText("导出性能采样 CSV").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("已导出性能采样：$target").fetchSemanticsNodes().isNotEmpty() }
            val saved = Files.readString(target)
            assertTrue(saved.contains("roll,,220,,,,,150.0,,90.0"))
            compose.onNodeWithText("导出性能采样 CSV").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("导出失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(saved, Files.readString(target))
        } finally { root.toFile().deleteRecursively() }
    }
}
