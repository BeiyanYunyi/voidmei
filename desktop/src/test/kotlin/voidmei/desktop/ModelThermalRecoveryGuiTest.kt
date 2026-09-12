package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelThermalRecoveryGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun separatesEngineAveragesAndShowsExcludedBandsWithoutStaleValues() {
        var models by mutableStateOf(listOf(
            EngineThermalParameters(1, listOf(EngineThermalBand(0, 80.0, null, null, null), EngineThermalBand(1, 90.0, null, 120.0, 60.0))),
            EngineThermalParameters(2, listOf(EngineThermalBand(1, null, 100.0, 90.0, 30.0)))))
        compose.setContent { MaterialTheme { Column { ModelThermalRecoveryPanel(models) } } }
        compose.onNodeWithText("发动机 #1 有效档位算术平均：2.000 s/s").assertExists()
        compose.onNodeWithText("发动机 #2 有效档位算术平均：3.000 s/s").assertExists()
        compose.onNodeWithText("参与 1 / 2 个档位").assertExists()
        compose.onNodeWithText("查看 #1 恢复档位").performClick()
        compose.onNodeWithText("Load0：— s/s").assertExists()
        compose.onNodeWithText("Load1：2.000 s/s").assertExists()
        compose.runOnIdle { models = emptyList() }
        compose.onNodeWithText("发动机 #1 有效档位算术平均：2.000 s/s").assertDoesNotExist()
        compose.onNodeWithText("没有可用发动机耐热恢复数据").assertExists()
    }
}
