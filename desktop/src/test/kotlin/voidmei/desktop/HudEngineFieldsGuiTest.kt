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

    @Test fun fieldSearchMatchesNamesIdsAndUnitsWithoutChangingSelection() {
        var ids by mutableStateOf(listOf("rpm", "future_field"))
        compose.setContent { MaterialTheme { Column(Modifier.width(600.dp)) {
            HudEngineFieldSettings(ids) { ids = it }
        } } }
        val search = compose.onNodeWithTag("hud-engine-field-search")
        search.performTextInput(" 推进  KW ")
        compose.onNodeWithTag("hud-engine-field-thrust_power").assertIsDisplayed()
        compose.onNodeWithTag("hud-engine-field-power").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-field-rpm").assertIsDisplayed()
        compose.runOnIdle { kotlin.test.assertEquals(listOf("rpm", "future_field"), ids) }
        compose.onNodeWithTag("hud-engine-field-thrust_power").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(listOf("rpm", "future_field", "thrust_power"), ids) }
        compose.onNodeWithText("没有匹配的可添加发动机字段").assertIsDisplayed()
        search.performTextReplacement("PROPULSIVE_EFFICIENCY")
        compose.onNodeWithTag("hud-engine-field-propulsive_efficiency").assertIsDisplayed()
        search.performTextReplacement("no_match")
        compose.onNodeWithText("没有匹配的可添加发动机字段").assertIsDisplayed()
        compose.onNodeWithTag("hud-engine-field-search-clear").performClick()
        compose.onNodeWithTag("hud-engine-field-power").assertExists()
        compose.runOnIdle { kotlin.test.assertEquals(listOf("rpm", "future_field", "thrust_power"), ids) }
    }

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
