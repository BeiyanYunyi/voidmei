package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.file.Files
import java.nio.file.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class LegacyAttitudeSizeGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dpiAndScreenAreRequiredAndSizePreviewMatchesCreatedRegion() {
        val imported = LegacySettingsReader.read("""(panel "地平仪" :x .8 :y .8
            (item w :target attitudeIndicatorWidth :type slider :value 100)
            (item h :target attitudeIndicatorHeight :type slider :value 100))""")
        var current by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600,
            listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 300, 200)))))
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).background(androidx.compose.ui.graphics.Color.White).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> imported }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        fun enter(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithText("迁移姿态窗口尺寸").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        click("补建缺少的对应分区（1 个）")
        // Ratio positions can still be imported before size information is provided.
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        enter("旧屏幕宽度", "1000"); enter("旧屏幕高度", "600")
        enter("原 DPI 缩放倍率（例如 1 或 1.5）", "1")
        click("迁移姿态窗口尺寸")
        compose.onNodeWithText("姿态尺寸 → region-1：104 × 104 dp，位置 (800, 480)（将应用）").assertExists()
        compose.onNodeWithText("姿态 → region-1：(800, 480) dp").assertExists()
        compose.onNodeWithText("姿态尺寸 → region-1：104 × 104 dp，位置 (800, 480)（将应用）").performScrollTo()
        val output = Path.of("build/hud-preview/legacy-attitude-size.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        enter("原 DPI 缩放倍率（例如 1 或 1.5）", "NaN")
        compose.onNodeWithText("旧外框：104 × 104；按原屏幕比例换算到当前画布。").assertDoesNotExist()
        enter("原 DPI 缩放倍率（例如 1 或 1.5）", "1")
        click("应用预览设置")
        compose.runOnIdle {
            val added = current.hudSceneLayout!!.regions.last()
            assertEquals(104, added.width); assertEquals(104, added.height)
            assertEquals(800, added.x); assertEquals(480, added.y)
        }
    }
}
