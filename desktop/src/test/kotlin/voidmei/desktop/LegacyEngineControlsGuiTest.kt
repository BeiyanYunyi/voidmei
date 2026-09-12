package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voidmei.config.AppSettings
import kotlin.test.*

class LegacyEngineControlsGuiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun previewAndApplyRetainEngineSelectionAndUseRpmControlInsteadOfBladeAngle() {
        val file = temporary.root.toPath().resolve("ui_layout.user.cfg")
        val text = """(panel p
            (item t :type switch-inv :target disableEngineInfoThrottle :value false)
            (item p :type switch-inv :target disableEngineInfoPitch :value true))"""
        Files.writeString(file, text)
        val original = AppSettings(hudFields = emptyList(), hudEngineIndex = 2,
            hudEngineFields = listOf("throttle"), hudAttitude = false, hudMechanization = false)
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Row(Modifier.size(950.dp, 600.dp)) {
            Column(Modifier.width(520.dp).verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { file.toString() }) { settings = it.applyTo(settings) }
            }
            Box(Modifier.width(430.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        compose.onNodeWithText("90 %").assertIsDisplayed()
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("选择旧版设置文件").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("发动机 转速控制：显示").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("旧引擎控制“桨距”对应转速控制百分比，不是桨叶角度。").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, settings) }
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.onNodeWithText("90 %").assertDoesNotExist()
        compose.onNodeWithText("发动机 #2").assertIsDisplayed()
        compose.onNodeWithText("70 %").assertIsDisplayed()
        compose.runOnIdle { assertEquals(original.copy(hudEngineFields = listOf("rpm_control")), settings) }
        assertEquals(text, Files.readString(file))
    }
}
