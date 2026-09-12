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
import voidmei.config.*
import voidmei.telemetry.FlightAlert
import kotlin.test.*

class LegacyVoicesGuiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun directoryIsOnlyAppliedAfterPreviewConfirmationAndMuteChoiceIsPreserved() {
        val root = temporary.root.toPath()
        val file = root.resolve("ui_layout.user.cfg")
        val alert = FlightAlert.CONNECTION_READY
        Files.writeString(file, """(panel p (item a :type voice :target voice_${alert.voice} :value "custom|false"))""")
        val voices = Files.createDirectories(root.resolve("voice/custom"))
        val bytes = javaClass.getResourceAsStream("/voice/${alert.voice}.wav")!!.use { it.readBytes() }
        Files.write(voices.resolve("${alert.voice}.wav"), bytes)
        val initial = AppSettings(voiceDirectory = "/current", voicePack = "another", voiceEnabled = true)
        var settings = initial
        val store = SettingsStore(root.resolve("settings-kmp.json"))
        store.load()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(chooseFile = { file.toString() }) {
                settings = it.applyTo(settings)
                store.save(settings)
            }
        } } }
        compose.onNodeWithText("导入旧版设置").performClick()
        compose.onNodeWithText("选择旧版设置文件").performScrollTo().performClick()
        compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
        val notice = "语音目录：${root.resolve("voice")}；确认后使用此目录中的外部语音文件，请保留该目录。"
        compose.waitUntil(5000) { compose.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(initial, settings); assertFalse(Files.exists(store.file)) }
        compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(initial.copy(voiceDirectory = root.resolve("voice").toString(),
                alertVoices = mapOf(alert.voice to VoiceChoice(false, "custom"))), settings)
            assertEquals(settings, SettingsStore(store.file).load().settings)
        }
        assertContentEquals(bytes, Files.readAllBytes(voices.resolve("${alert.voice}.wav")))
    }
}
