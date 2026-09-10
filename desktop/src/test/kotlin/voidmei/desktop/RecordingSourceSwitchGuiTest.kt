package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import voidmei.recording.*

class RecordingSourceSwitchGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoreCancelsAnalysisAndAllowsAnotherRangeBeforeTheOldWorkReturns() {
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,300\n1,2000,1000,test,400\n"
        val entered = AtomicBoolean()
        val finished = AtomicBoolean()
        val release = CountDownLatch(1)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                RecordingWindowPanel(text, FlightRecordPlots.analyze(text), listOf("ias_kmh" to "IAS"),
                    analyzeWindow = { source, start, end, fields ->
                        if (start == 0L) {
                            entered.set(true)
                            check(release.await(15, TimeUnit.SECONDS))
                        }
                        FlightRecordWindow.analyze(source, start, end, fields).also {
                            if (start == 0L) finished.set(true)
                        }
                    })
            } } }
            compose.onNodeWithText("分析此区间").performScrollTo().performClick()
            compose.waitUntil(5000) { entered.get() }
            compose.onNodeWithText("恢复完整记录").performClick()
            compose.onNodeWithText("分析此区间").assertIsEnabled()
            compose.onNodeWithText("当前曲线：0.0–1.0 s · 2 帧").assertExists()
            compose.onNodeWithText("区间起点 (s)").performTextReplacement("1")
            compose.onNodeWithText("分析此区间").performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("当前曲线：1.0–1.0 s · 1 帧").fetchSemanticsNodes().isNotEmpty()
            }
            release.countDown()
            compose.waitUntil(5000) { finished.get() }
            compose.waitForIdle()
            compose.onNodeWithText("当前曲线：1.0–1.0 s · 1 帧").assertExists()
        } finally { release.countDown() }
    }

    @Test fun switchingSourceReleasesControlsWhilePreviousAnalysisFinishes() {
        val first = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,first,300\n1,2000,1000,first,400\n"
        val second = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n10,3000,0,second,500\n11,5000,2000,second,600\n"
        var text by mutableStateOf(first)
        val entered = AtomicBoolean()
        val finished = AtomicBoolean()
        val release = CountDownLatch(1)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                RecordingWindowPanel(text, FlightRecordPlots.analyze(text), listOf("ias_kmh" to "IAS"),
                    analyzeWindow = { source, start, end, fields ->
                        if (source == first) {
                            entered.set(true)
                            check(release.await(15, TimeUnit.SECONDS))
                        }
                        FlightRecordWindow.analyze(source, start, end, fields).also {
                            if (source == first) finished.set(true)
                        }
                    })
            } } }
            compose.onNodeWithText("分析此区间").performScrollTo().performClick()
            compose.waitUntil(5000) { entered.get() }
            compose.onNodeWithText("分析此区间").assertIsNotEnabled()
            compose.runOnIdle { text = second }
            compose.onNodeWithText("分析此区间").assertIsEnabled()
            compose.onNodeWithText("区间终点 (s)").assertTextContains("2.0")
            compose.onNodeWithText("区间起点 (s)").performTextReplacement("2")
            compose.onNodeWithText("分析此区间").performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("当前曲线：2.0–2.0 s · 1 帧").fetchSemanticsNodes().isNotEmpty()
            }
            release.countDown()
            compose.waitUntil(5000) { finished.get() }
            compose.waitForIdle()
            compose.onNodeWithText("当前曲线：2.0–2.0 s · 1 帧").assertExists()
            compose.onNodeWithText("分析此区间").assertIsEnabled()
        } finally { release.countDown() }
    }
}
