package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelLiftParametersGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun distinguishesRawAndEffectiveAreaAndClearsMissingData() {
        var parameters by mutableStateOf(FlightModelParameters(2000.0, null, emptyList(), false, emptyList(),
            aerodynamicGeometry = listOf(WingGeometry("WingPlane", null, 9.0, 6.0, -5.0, .8, "OswaldsEfficiencyNumber", null, null)),
            stallSpeed = StallSpeedModel(2000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0)))))
        compose.setContent { MaterialTheme { Column { ModelLiftParametersPanel(parameters) } } }
        compose.onNodeWithText("机翼面积：9.0000 m² · 翼展：6.0000 m · 展弦比：4.0000").assertExists()
        compose.onNodeWithText("机身面积：— m² · 来源：未知").assertExists()
        compose.onNodeWithText("后掠 0.0000：无襟翼 10.0000 / 满襟翼 20.0000 m²").assertExists()
        compose.onNodeWithText("按空重归一化：5.0000 / 10.0000 m²/t").assertExists()
        compose.runOnIdle { parameters = parameters.copy(emptyMassKg = null) }
        compose.onNodeWithText("按空重归一化：— / — m²/t").assertExists()
        compose.runOnIdle { parameters = parameters.copy(stallSpeed = null, aerodynamicGeometry = emptyList()) }
        compose.onNodeWithText("机翼来源：WingPlane").assertDoesNotExist()
        compose.onNodeWithText("未提供可识别的机翼几何来源").assertExists()
        compose.onNodeWithText("缺少完整升力模型，派生升力面积未知").assertExists()
    }
}
