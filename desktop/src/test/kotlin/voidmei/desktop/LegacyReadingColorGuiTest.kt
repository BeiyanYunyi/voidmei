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
import voidmei.config.AppSettings

class LegacyReadingColorGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewExplainsPartialScopeAndOnlyApplyChangesColors() {
        val path = Files.createTempFile("legacy-colors", ".cfg")
        val text = """(panel p (item c :type color :target fontNum :value "12, 34, 56, 128"))"""
        val initial = AppSettings(hudValueColor = "#FFFFFF", hudUnitColor = "#00FF00")
        var current = initial
        try {
            Files.writeString(path, text)
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { path.toString() }) { current = it.applyTo(current) }
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("HUD 普通读数颜色：#0C223880").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("配色仅应用于 HUD 飞行与发动机表格；旧版其他文字和图形填充尚未迁移。RGBA 末两位为透明度。").assertExists()
            compose.runOnIdle { assertEquals(initial, current) }
            compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(initial.copy(hudValueColor = "#0C223880"), current) }
            assertEquals(text, Files.readString(path))
        } finally { Files.deleteIfExists(path) }
    }
}
