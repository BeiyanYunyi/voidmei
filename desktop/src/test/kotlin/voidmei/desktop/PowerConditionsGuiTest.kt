package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PowerConditionsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editsRequireApplyAndInvalidConditionsKeepLastValues() {
        var speed by mutableStateOf(0.0)
        var temperature by mutableStateOf(15.0)
        var equivalent by mutableStateOf(false)
        var applied = 0
        compose.setContent { MaterialTheme {
            PowerConditionsPanel(speed, temperature, equivalent) { s, t -> speed = s; temperature = t; applied++ }
        } }
        compose.onNodeWithText("自定义速度 TAS (km/h)").performTextReplacement("425.5")
        compose.onNodeWithText("自定义海平面温度 (°C)").performTextReplacement("22.5")
        compose.runOnIdle { assertEquals(0, applied) }
        compose.onNodeWithText("应用功率条件").performClick()
        compose.onNodeWithText("已应用：TAS 425.50 km/h · 海平面 22.50°C").assertExists()
        for ((s, t) in listOf("NaN" to "15", "-1" to "15", "400" to "-273.15", "400" to "Infinity")) {
            compose.onNodeWithText("自定义速度 TAS (km/h)").performTextReplacement(s)
            compose.onNodeWithText("自定义海平面温度 (°C)").performTextReplacement(t)
            compose.onNodeWithText("应用功率条件").performClick()
            compose.onNodeWithText("已应用：TAS 425.50 km/h · 海平面 22.50°C").assertExists()
        }
        compose.runOnIdle { assertEquals(1, applied); speed = 600.0; temperature = 30.0; equivalent = true }
        compose.onNodeWithText("自定义速度 EAS (km/h)").assertTextContains("600.0")
        compose.onNodeWithText("自定义海平面温度 (°C)").assertTextContains("30.0")
        compose.onNodeWithText("已应用：EAS 600.00 km/h · 海平面 30.00°C").assertExists()
    }
}
