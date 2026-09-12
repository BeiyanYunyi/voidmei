package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.WepFuelModel

class ModelWepFuelGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sharedTankDurationAndEngineRatesClearWhenModelDisappears() {
        var model by mutableStateOf<WepFuelModel?>(WepFuelModel(120.0, mapOf(2 to .25, 1 to .25, 3 to 0.0)))
        compose.setContent { MaterialTheme { Column { ModelWepFuelPanel(model) } } }
        compose.onNodeWithText("整机共享加力燃料容量：120.00 kg").assertExists()
        compose.onNodeWithText("发动机 #3 模型消耗率：0.0000 kg/s").assertExists()
        compose.onNodeWithText("全发动机持续加力理论时限：4.00 分钟").assertExists()
        for (invalid in listOf(null, WepFuelModel(120.0, mapOf(1 to 0.0)))) {
            compose.runOnIdle { model = invalid }
            compose.onNodeWithText("全发动机持续加力理论时限：4.00 分钟").assertDoesNotExist()
            compose.onNodeWithText("未提供可计算的加力燃料模型").assertExists()
        }
    }
}
