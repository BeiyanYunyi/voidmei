package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelInertiaGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun displaysLegacyAxisOrderAndClearsMissingVector() {
        var inertia by mutableStateOf<ModelInertia?>(ModelInertiaExtractor.extract(BlkParser.parse("Mass { MomentOfInertia:p3=100.25,200.5,300.75 }")).inertia)
        compose.setContent { MaterialTheme { Column { ModelInertiaPanel(inertia) } } }
        compose.onNodeWithText("俯仰 P：300.750").assertExists()
        compose.onNodeWithText("滚转 R：100.250").assertExists()
        compose.onNodeWithText("偏航 Y：200.500").assertExists()
        compose.onNodeWithText("来源：Mass.MomentOfInertia").assertExists()
        compose.runOnIdle { inertia = null }
        compose.onNodeWithText("俯仰 P：300.750").assertDoesNotExist()
        compose.onNodeWithText("俯仰 P：—").assertExists()
        compose.onNodeWithText("滚转 R：—").assertExists()
        compose.onNodeWithText("偏航 Y：—").assertExists()
        compose.onNodeWithText("模型未提供有效转动惯量向量").assertExists()
    }
}
