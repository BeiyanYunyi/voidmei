package voidmei.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.ConnectionState
import kotlin.test.assertEquals

class HudOpacityGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun zeroBackgroundRevealsUnderlyingContentAndKeepsHudText() {
        var opacity by mutableStateOf(0f)
        compose.setContent { MaterialTheme {
            Box(Modifier.size(300.dp, 300.dp).background(Color.Red).testTag("backdrop")) {
                HudPanel(ConnectionState.WaitingForFlight, AppSettings(hudOpacity = opacity), emptyList(), null) {
                    Text("HUD remains visible")
                }
            }
        } }
        fun backgroundPixel() = compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()[1, 1]
        assertEquals(Color.Red, backgroundPixel())
        compose.onNodeWithText("HUD remains visible").assertIsDisplayed()
        compose.runOnIdle { opacity = 1f }
        assertEquals(Color(0xFF111820), backgroundPixel())
        compose.onNodeWithText("HUD remains visible").assertIsDisplayed()
        compose.runOnIdle { opacity = 0f }
        assertEquals(Color.Red, backgroundPixel())
    }
}
