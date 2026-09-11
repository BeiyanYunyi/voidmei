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
import kotlin.test.assertTrue

class HudEngineFieldsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedReadingsRetainOrderMissingValuesAndEngineIdentity() {
        var fields by mutableStateOf(listOf(HudEngineField.OIL_TEMPERATURE, HudEngineField.THROTTLE))
        var engines by mutableStateOf(listOf(Engine(1, 99.0, null, null, null, null, null), Engine(2, 0.0, null, null, null, null, null)))
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalReadingColumns provides 1) {
                Box(Modifier.width(300.dp)) { HudEnginePanel(engines, 2, fields = fields) }
            }
        } }
        compose.onNodeWithText("发动机 #2").assertExists()
        compose.onNodeWithText("— °C").assertExists()
        compose.onNodeWithText("0 %").assertExists()
        compose.onNodeWithText("99 %").assertDoesNotExist()
        compose.onNodeWithText("转速").assertDoesNotExist()
        assertTrue(compose.onNodeWithText("油温").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("油门").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { fields = emptyList() }
        compose.onNodeWithText("未选择发动机读数").assertExists()
        compose.onNodeWithText("0 %").assertDoesNotExist()
        compose.runOnIdle { engines = engines.take(1) }
        compose.onNodeWithText("此编号无可用发动机数据").assertExists()
        compose.onNodeWithText("99 %").assertDoesNotExist()
    }
}
