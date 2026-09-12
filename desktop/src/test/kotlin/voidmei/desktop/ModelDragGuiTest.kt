package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelDragGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun presentsFormulasMassAndUnknownValuesAndClearsChangedModel() {
        val clean = AerodynamicPart(AerodynamicPartKind.CLEAN, "NoFlaps", null, .1, null, null, null, null, null)
        val body = clean.copy(kind = AerodynamicPartKind.FUSELAGE, sourcePath = "Fuselage", cdMin = .05)
        var parameters by mutableStateOf(FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList(),
            basicMassKg = 1050.0,
            aerodynamicGeometry = listOf(WingGeometry("旧根字段", null, 9.0, 6.0, null, .8, "OswaldsEfficiencyNumber", 2.0, "Areas.Fuselage")),
            aerodynamicParts = listOf(clean, body), radiatorDrag = listOf(RadiatorDrag("RadiatorCd", 0.0))))
        compose.setContent { MaterialTheme { Column { ModelDragPanel(parameters) } } }
        compose.onNodeWithText("主阻力面积 CdS：1.0000 m² · 诱导阻力因数 k：0.0995").assertExists()
        compose.onNodeWithText("半油参考质量：1100.0000 kg").assertExists()
        compose.onNodeWithText("CdS／质量：0.9091 m²/t · 质量 × k：109.4190 kg").assertExists()
        compose.onNodeWithText("RadiatorCd：0.0000").assertExists()
        compose.runOnIdle { parameters = parameters.copy(basicMassKg = null) }
        compose.onNodeWithText("半油参考质量：— kg").assertExists()
        compose.runOnIdle { parameters = parameters.copy(aerodynamicGeometry = emptyList(), radiatorDrag = emptyList()) }
        compose.onNodeWithText("缺少机翼几何来源，阻力参考未知").assertExists()
        compose.onNodeWithText("未提供散热器阻力系数").assertExists()
        compose.onNodeWithText("RadiatorCd：0.0000").assertDoesNotExist()
    }
}
