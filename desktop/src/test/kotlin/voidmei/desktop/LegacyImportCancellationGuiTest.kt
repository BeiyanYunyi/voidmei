package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import voidmei.config.LegacySettings
import kotlin.test.assertEquals

class LegacyImportCancellationGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelledReadCannotReplaceNewPreviewOrApplyOldSettings() {
        val entered = AtomicBoolean()
        val finished = AtomicBoolean()
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val old = LegacySettings(100, null, null)
        val fresh = LegacySettings(10, null, null)
        var applied: LegacySettings? = null
        try {
            compose.setContent { MaterialTheme { Column {
                LegacySettingsPanel(readSettings = { _, _ ->
                    if (calls.incrementAndGet() == 1) {
                        entered.set(true)
                        check(release.await(15, TimeUnit.SECONDS))
                        finished.set(true)
                        old
                    } else fresh
                }, onApply = { applied = it })
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { entered.get() }
            compose.onNodeWithText("取消读取").performClick()
            compose.onNodeWithText("旧版布局文件路径（UTF-8）").assertIsEnabled().performTextReplacement("fresh.cfg")
            compose.onNodeWithText("预览旧设置").assertIsEnabled().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("来源：fresh.cfg").fetchSemanticsNodes().isNotEmpty() }
            release.countDown()
            compose.waitUntil(5000) { finished.get() }
            compose.waitForIdle()
            compose.onNodeWithText("来源：fresh.cfg").assertExists()
            compose.onNodeWithText("应用预览设置").performClick()
            compose.runOnIdle { assertEquals(fresh, applied) }
        } finally { release.countDown() }
    }
}
