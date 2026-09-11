package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import kotlin.test.*

class SoftwareRenderingGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preferenceUpdatesWithoutChangingHudCompatibilityOrCurrentRenderer() {
        var settings by mutableStateOf(AppSettings(hudCompatibilityMode = true))
        val before = System.getProperty("skiko.renderApi")
        compose.setContent { MaterialTheme { Column {
            SoftwareRenderingSettings(settings.softwareRendering) { settings = settings.copy(softwareRendering = it) }
        } } }
        compose.onNodeWithText("软件渲染（重启后生效）").assertExists()
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertTrue(settings.softwareRendering)
            assertTrue(settings.hudCompatibilityMode)
            assertEquals(before, System.getProperty("skiko.renderApi"))
        }
        compose.onNode(isToggleable()).performClick().assertIsOff()
    }
}
