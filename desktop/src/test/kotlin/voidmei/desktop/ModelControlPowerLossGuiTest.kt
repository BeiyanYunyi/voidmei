package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.ControlPowerLoss

class ModelControlPowerLossGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun showsRawCoefficientsAndClearsMissingChannels() {
        var loss by mutableStateOf(ControlPowerLoss(0.0, 1.2345, -.25))
        compose.setContent { MaterialTheme { Column { ModelControlPowerLoss(loss) } } }
        compose.onNodeWithText("副翼 AileronPowerLoss：0.000").assertExists()
        compose.onNodeWithText("升降舵 ElevatorPowerLoss：1.235").assertExists()
        compose.onNodeWithText("方向舵 RudderPowerLoss：-0.250").assertExists()
        compose.runOnIdle { loss = ControlPowerLoss() }
        compose.onNodeWithText("升降舵 ElevatorPowerLoss：1.235").assertDoesNotExist()
        compose.onNodeWithText("副翼 AileronPowerLoss：—").assertExists()
        compose.onNodeWithText("升降舵 ElevatorPowerLoss：—").assertExists()
        compose.onNodeWithText("方向舵 RudderPowerLoss：—").assertExists()
    }
}
