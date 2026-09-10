package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class OfflineModelGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadsWithoutTelemetryAndCanRecoverFromMissingAircraft() {
        val root = Files.createTempDirectory("voidmei-offline-model")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        try {
            Files.writeString(directory.resolve("first.blkx"), "fmFile:t=\"fm/first.blk\"")
            Files.writeString(directory.resolve("fm/first.blkx"), "Mass { EmptyMass:r=2500 }")
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                OfflineModelPanel(root.toString())
            } } }
            compose.onNodeWithText("查看机型", substring = false).assertIsNotEnabled()
            compose.onNodeWithText("离线机型编号").performTextInput(" FIRST ")
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertDoesNotExist()
            compose.onNodeWithText("查看机型", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("模型空重 2500.00 kg", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("查看机型：first").assertExists()
            compose.onNodeWithText("设为比较基准").performScrollTo().performClick()
            compose.onNodeWithText("比较基准：first").assertExists()
            val baselineSource = compose.onNodeWithTag("comparison-baseline-source").fetchSemanticsNode()
                .config[SemanticsProperties.Text].single().text
            compose.onNodeWithTag("comparison-baseline-source").assertTextContains(directory.resolve("fm/first.blkx").toString(), substring = true)
            compose.onNodeWithText("离线机型编号").performScrollTo()
            compose.onNodeWithText("离线机型编号").performTextReplacement("../outside")
            compose.onNodeWithText("查看机型", substring = false).performClick()
            compose.onNodeWithText("机型编号仅支持", substring = true).assertExists()
            compose.onNodeWithText("查看机型：first").assertExists()
            compose.onNodeWithText("离线机型编号").performTextReplacement("second")
            compose.onNodeWithText("查看机型", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("未找到 second", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertDoesNotExist()
            Files.writeString(directory.resolve("second.blkx"), "fmFile:t=\"fm/second.blk\"")
            Files.writeString(directory.resolve("fm/second.blkx"), "Mass { EmptyMass:r=3200 }")
            compose.onNodeWithText("重新加载").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("模型空重 3200.00 kg", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("模型比较：first → second").assertExists()
            compose.onNodeWithTag("model-comparison-空重").assertTextContains("2500.00")
                .assertTextContains("3200.00").assertTextContains("700.00")
            compose.onNodeWithTag("comparison-current-source").assertTextContains(directory.resolve("fm/second.blkx").toString(), substring = true)
            Files.writeString(directory.resolve("fm/second.blkx"), "Mass { EmptyMass:r=3400 }")
            compose.onNodeWithText("重新加载").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("模型空重 3400.00 kg", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("comparison-baseline-source").assertTextEquals(baselineSource)
            compose.onNodeWithTag("model-comparison-空重").assertTextContains("2500.00")
                .assertTextContains("3400.00").assertTextContains("900.00")
            compose.onNodeWithText("清除比较基准").performScrollTo().performClick()
            compose.onNodeWithTag("model-comparison-空重").assertDoesNotExist()
        } finally { root.toFile().deleteRecursively() }
    }
}
