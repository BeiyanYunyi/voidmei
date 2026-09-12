package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.file.Files
import java.nio.file.Path
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.UnmigratedLegacySetting

class LegacyUnmigratedReportGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchesBeyondOldLimitAndKeepsScopedDuplicatesAndQueryAcrossCollapse() {
        val entries = (1..125).map { UnmigratedLegacySetting("面板 项目$it", "key$it") } +
            listOf(UnmigratedLegacySetting("飞行透明度", ":alpha"), UnmigratedLegacySetting("姿态透明度", ":alpha"))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).background(Color.White).verticalScroll(rememberScrollState())) {
            LegacyUnmigratedReport(entries)
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun search(value: String) = compose.onNodeWithTag("legacy-unmigrated-search").performScrollTo().performTextReplacement(value)
        click("未迁移 127 项：查看")
        compose.onNodeWithText("匹配 127 / 127 项，已显示 25 项").assertExists()
        compose.onNodeWithText("面板 项目26（key26）").assertDoesNotExist()
        compose.onNodeWithTag("legacy-unmigrated-more").performScrollTo().performClick()
        compose.onNodeWithText("面板 项目26（key26）").assertExists()
        search("面板 KEY125")
        compose.onNodeWithText("面板 项目125（key125）").assertExists()
        compose.onNodeWithText("匹配 1 / 127 项，已显示 1 项").assertExists()
        click("未迁移 127 项：收起"); click("未迁移 127 项：查看")
        compose.onNodeWithTag("legacy-unmigrated-search").assertTextContains("面板 KEY125")
        search(":alpha")
        compose.onNodeWithText("飞行透明度（:alpha）").assertExists()
        compose.onNodeWithText("姿态透明度（:alpha）").assertExists()
        val output = Path.of("build/hud-preview/unmigrated-search.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        search("不存在")
        compose.onNodeWithText("没有匹配的未迁移项目。请更换关键词或清除搜索。").assertExists()
        click("清除未迁移搜索")
        compose.onNodeWithText("匹配 127 / 127 项，已显示 25 项").assertExists()
    }
    @Test fun sourcePathsDisambiguateIdenticalLabelsAndCanBeSearched() {
        val entries = voidmei.config.LegacySettingsReader.read("""(panel "MiniHUD" (group "显示" (group "字体"
            (item "大小" :target fontSize :type slider :value 3))))
            (panel "舵面值" (group "显示" (item "大小" :target fontSize :type slider :value 4)))""").unmigrated
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).background(Color.White).verticalScroll(rememberScrollState())) {
            LegacyUnmigratedReport(entries)
        } } }
        compose.onNodeWithText("未迁移 2 项：查看").performClick()
        compose.onAllNodesWithText("大小（fontSize）").assertCountEquals(2)
        compose.onNodeWithText("来源：MiniHUD / 显示 / 字体").assertExists()
        compose.onNodeWithText("来源：舵面值 / 显示").assertExists()
        compose.onNodeWithTag("legacy-unmigrated-search").performTextReplacement("MiniHUD 字体 FONTSIZE")
        compose.onAllNodesWithText("大小（fontSize）").assertCountEquals(1)
        compose.onNodeWithText("来源：舵面值 / 显示").assertDoesNotExist()
        compose.onNodeWithText("匹配 1 / 2 项，已显示 1 项").assertExists()
        val output = Path.of("build/hud-preview/unmigrated-sources.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
    }

}
