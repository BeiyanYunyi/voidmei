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
import org.junit.Assert.*
import java.nio.file.Files
import voidmei.config.*

class OfflineModelPreferencesGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoresFreshBaselineAndRecoversMissingFilesWithoutStaleComparisons() {
        val root = Files.createTempDirectory("voidmei-offline-saved")
        val other = Files.createTempDirectory("voidmei-offline-other")
        fun model(base: java.nio.file.Path, name: String, mass: Int) {
            val directory = Files.createDirectories(base.resolve("aces/gamedata/flightmodels/fm")).parent
            Files.writeString(directory.resolve("$name.blkx"), "fmFile:t=\"fm/${name}_model.blk\"")
            Files.writeString(directory.resolve("fm/${name}_model.blkx"), "Mass { EmptyMass:r=$mass }")
        }
        var settings by mutableStateOf(AppSettings(fmDataRoot = "live-only", offlineModels = OfflineModelPreferences(root.toString(), "second", "first")))
        var generation by mutableStateOf(0)
        fun waitText(text: String) = compose.waitUntil(10000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        try {
            model(root, "first", 2500); model(root, "second", 3200)
            compose.setContent { MaterialTheme { key(generation) { Column(Modifier.verticalScroll(rememberScrollState())) {
                OfflineModelPanel(settings.fmDataRoot, preferences = settings.offlineModels,
                    onPreferences = { settings = settings.copy(offlineModels = it) })
            } } } }
            waitText("模型比较：first → second")
            waitText("模型空重 3200.00 kg")
            compose.onNodeWithTag("model-comparison-空重").assertTextContains("2500.00").assertTextContains("700.00")
            model(root, "first", 2700)
            compose.runOnIdle { settings = SettingsJson.decode(SettingsJson.encode(settings)); generation++ }
            waitText("模型比较：first → second")
            waitText("模型空重 3200.00 kg")
            compose.onNodeWithTag("model-comparison-空重").assertTextContains("2700.00").assertTextContains("500.00")
            compose.onNodeWithText("离线数据目录").performScrollTo().performTextReplacement(other.toString())
            compose.onNodeWithText("应用离线目录").performClick()
            waitText("未找到比较基准 first")
            compose.onNodeWithTag("model-comparison-空重").assertDoesNotExist()
            assertEquals("live-only", settings.fmDataRoot)
            assertEquals(other.toString(), settings.offlineModels.dataRoot)
            model(other, "first", 2900)
            compose.onNodeWithText("重新加载比较基准").performScrollTo().performClick()
            waitText("比较基准：first")
            compose.onNodeWithText("清除比较基准").performScrollTo().performClick()
            compose.runOnIdle { assertNull(settings.offlineModels.baselineAircraft) }
            compose.onNodeWithText("清除当前离线机型").performScrollTo().performClick()
            compose.runOnIdle { assertNull(settings.offlineModels.aircraft) }
            compose.onNodeWithText("离线机型编号").performScrollTo().performTextReplacement("../invalid")
            compose.onNodeWithText("查看机型", substring = false).performClick()
            compose.onNodeWithText("机型编号仅支持", substring = true).assertExists()
            compose.runOnIdle { assertNull(settings.offlineModels.aircraft) }
            compose.onNodeWithText("离线机型编号").performTextReplacement(" FIRST ")
            compose.onNodeWithText("查看机型", substring = false).performClick()
            waitText("模型空重 2900.00 kg")
            compose.onNodeWithText("设为比较基准").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(OfflineModelPreferences(other.toString(), "first", "first"), settings.offlineModels) }
        } finally { root.toFile().deleteRecursively(); other.toFile().deleteRecursively() }
    }
}
