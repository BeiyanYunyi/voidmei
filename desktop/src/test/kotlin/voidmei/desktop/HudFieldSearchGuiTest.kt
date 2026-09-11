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
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.HudField
import kotlin.test.assertEquals

class HudFieldSearchGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchMatchesNamesIdsAndUnitsWithoutMutatingExistingOrder() {
        var selected by mutableStateOf(listOf(HudField.IAS))
        compose.setContent { MaterialTheme {
            Column(Modifier.width(500.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                HudFieldPicker(selected) { selected = selected + it }
            }
        } }
        compose.onNodeWithTag("hud-field-search").performTextReplacement("  FUEL_PERCENT  ")
        compose.onNodeWithTag("hud-add-fuel_percent").assertExists()
        compose.onNodeWithTag("hud-add-tas").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(HudField.IAS), selected) }
        compose.onNodeWithTag("hud-add-fuel_percent").performScrollTo().performClick()
        compose.onNodeWithTag("hud-add-fuel_percent").assertDoesNotExist()
        compose.onNodeWithTag("hud-field-search").performTextReplacement("no_such_field")
        compose.onNodeWithText("没有匹配的可添加字段").assertExists()
        compose.runOnIdle { assertEquals(listOf(HudField.IAS, HudField.FUEL_PERCENT), selected) }
        compose.onNodeWithTag("hud-field-search").performTextReplacement("推力 kgf")
        compose.onNodeWithTag("hud-add-thrust").assertExists()
        compose.onNodeWithTag("hud-add-power").assertDoesNotExist()
        compose.onNodeWithTag("hud-field-search").performTextReplacement("km/h")
        compose.onNodeWithTag("hud-add-tas").assertExists()
        compose.onNodeWithTag("hud-add-ias").assertDoesNotExist()
        compose.onNodeWithText("清除字段搜索").performClick()
        compose.onNodeWithTag("hud-add-sep").assertExists()
        compose.runOnIdle { selected = HudField.entries }
        compose.onNodeWithText("已添加全部字段").assertExists()
    }
}
