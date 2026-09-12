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
import org.junit.rules.TemporaryFolder
import voidmei.config.AppSettings
import kotlin.test.*

class LegacyRendererGuiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun previewUsesSidecarAndConfirmationPersistsWithoutChangingCurrentBackend() {
        val root = temporary.root.toPath()
        val layout = root.resolve("ui_layout.user.cfg")
        Files.writeString(layout, """(panel p (item r :type switch :target gpuCompatibilityMode :value false))""")
        val sidecar = root.resolve("gpu_compat.properties")
        Files.writeString(sidecar, "softwareRenderingEnabled=true\n")
        val original = AppSettings(hudCompatibilityMode = true)
        var settings = original
        val store = SettingsStore(root.resolve("settings-kmp.json"))
        store.load()
        val backend = System.getProperty("skiko.renderApi")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(chooseFile = { layout.toString() }) {
                settings = it.applyTo(settings)
                store.save(settings)
            }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("选择旧版设置文件").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("渲染设置来源：$sidecar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("软件渲染：开启（重启后生效）；保留当前 HUD 兼容显示选择。").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, settings); assertFalse(Files.exists(store.file)) }
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original.copy(softwareRendering = true), settings)
            assertEquals(settings, SettingsStore(store.file).load().settings)
            assertEquals(backend, System.getProperty("skiko.renderApi"))
        }
        assertEquals("softwareRenderingEnabled=true\n", Files.readString(sidecar))
    }
}
