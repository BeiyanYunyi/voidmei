package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import voidmei.recording.*

class PerformanceCancellationGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelAllowsImmediateRetryAndRejectsLateOldResults() {
        val entered = AtomicBoolean()
        val finished = AtomicBoolean()
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val old = FlightPerformanceAnalysis(listOf(ClimbSample(100, 0, null, null, null)), emptyList(), emptyList())
        val fresh = old.copy(climb = old.climb + ClimbSample(200, 1000, null, null, null))
        try {
            compose.setContent { MaterialTheme { Column {
                PerformanceAnalysisPanel("test", analyze = {
                    if (calls.incrementAndGet() == 1) {
                        entered.set(true)
                        check(release.await(15, TimeUnit.SECONDS))
                        finished.set(true)
                        old
                    } else fresh
                })
            } } }
            compose.onNodeWithText("统计爬升与机动采样").performClick()
            compose.waitUntil(5000) { entered.get() }
            compose.onNodeWithText("取消统计").performClick()
            compose.onNodeWithText("统计爬升与机动采样").assertIsEnabled().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("高度档 2 · 滚转速度档 0 · 过载速度档 0").fetchSemanticsNodes().isNotEmpty() }
            release.countDown()
            compose.waitUntil(5000) { finished.get() }
            compose.waitForIdle()
            compose.onNodeWithText("高度档 2 · 滚转速度档 0 · 过载速度档 0").assertExists()
            compose.onNodeWithText("高度档 1 · 滚转速度档 0 · 过载速度档 0").assertDoesNotExist()
            compose.onNodeWithText("统计爬升与机动采样").assertIsEnabled()
        } finally { release.countDown() }
    }
}
