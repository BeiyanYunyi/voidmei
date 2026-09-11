package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*
import kotlin.test.*

class HudEngineOrderGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingSelectedFieldsUpdatesReadingsAndRetainsUnknownPreferences() {
        var ids by mutableStateOf(listOf("throttle", "future", "rpm"))
        val engine = Engine(1, 42.0, 1500.0, null, null, null, null)
        compose.setContent { MaterialTheme { Row(Modifier.width(800.dp)) {
            Column(Modifier.width(500.dp)) { HudEngineFieldSettings(ids) { ids = it } }
            CompositionLocalProvider(LocalReadingColumns provides 1) {
                Box(Modifier.width(300.dp)) { HudEnginePanel(listOf(engine), 1, fields = HudEngineField.selected(ids)) }
            }
        } } }
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("42 %") < top("1500 RPM"))
        compose.onNodeWithTag("hud-engine-up-throttle").assertIsNotEnabled()
        compose.onNodeWithTag("hud-engine-down-rpm").assertIsNotEnabled()
        compose.onNodeWithTag("hud-engine-up-rpm").performClick()
        compose.runOnIdle { assertEquals(listOf("rpm", "future", "throttle"), ids) }
        assertTrue(top("1500 RPM") < top("42 %"))
        compose.onNodeWithTag("hud-engine-field-rpm").performClick()
        compose.onNodeWithText("1500 RPM").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-field-rpm").performClick()
        compose.runOnIdle { assertEquals(listOf("future", "throttle", "rpm"), ids) }
        assertTrue(top("42 %") < top("1500 RPM"))
    }
}
