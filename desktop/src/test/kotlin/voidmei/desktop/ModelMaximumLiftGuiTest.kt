package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelMaximumLiftGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun displaysExplicitFuelReferencesAndClearsUnavailableModels() {
        var parameters by mutableStateOf(FlightModelParameters(1000.0, 1000.0, emptyList(), false, emptyList(),
            stallSpeed = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0)))))
        compose.setContent { MaterialTheme { Column { ModelMaximumLiftPanel(parameters) } } }
        compose.onNodeWithText("不含燃油 · 燃油 0.00 kg：无襟翼 5.91 G / 满襟翼 11.82 G").assertExists()
        compose.onNodeWithText("半油 · 燃油 500.00 kg：", substring = true).assertExists()
        compose.onNodeWithText("满油 · 燃油 1000.00 kg：无襟翼 2.95 G / 满襟翼 5.91 G").assertExists()
        compose.runOnIdle { parameters = parameters.copy(maximumFuelMassKg = null) }
        compose.onNodeWithText("半油 ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("缺少有效燃油容量，仅显示不含燃油参考。").assertExists()
        compose.runOnIdle { parameters = parameters.copy(stallSpeed = null) }
        compose.onNodeWithText("不含燃油 ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("缺少有效升力模型，无法估算").assertExists()
    }
}
