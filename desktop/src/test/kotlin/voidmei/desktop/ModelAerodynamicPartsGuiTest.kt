package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelAerodynamicPartsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun separateProfilesPreserveSourcesSignsAndUnknownValues() {
        var parts by mutableStateOf(AerodynamicPartsExtractor.extract(BlkParser.parse("""
            WingPlaneSweep0 { Sweep:r=0; NoFlaps { CdMin:r=0.01; Cl0:r=-0.2; alphaCritLow:r=-10; alphaCritHigh:r=15 } }
            WingPlaneSweep1 { Sweep:r=1; FlapsPolar0 { ClCritLow:r=-1; ClCritHigh:r=1.5 } }
        """)).parts)
        compose.setContent { MaterialTheme { Column { ModelAerodynamicPartsPanel(AerodynamicPartKind.CLEAN, parts) } } }
        compose.onNodeWithText("来源：WingPlaneSweep0.NoFlaps").assertExists()
        compose.onNodeWithText("来源：WingPlaneSweep1.FlapsPolar0").assertExists()
        compose.onNodeWithText("CdMin：0.0100 · Cl0：-0.2000").assertExists()
        compose.onNodeWithText("原始临界迎角：-10.0000 ～ 15.0000 °").assertExists()
        compose.onNodeWithText("CdMin：— · Cl0：—").assertExists()
        compose.onNodeWithText("后掠比例：1.0000").assertExists()
        compose.runOnIdle { parts = emptyList() }
        compose.onNodeWithText("来源：WingPlaneSweep0.NoFlaps").assertDoesNotExist()
        compose.onNodeWithText("未提供无襟翼器件参数").assertExists()
    }
    @Test fun paginatesSourcesAndResetsWhenTheModelChanges() {
        var parts by mutableStateOf((0..8).map { AerodynamicPart(AerodynamicPartKind.CLEAN, "WingPlaneSweep$it.NoFlaps", it / 8.0, 0.0, null, null, null, null, null) })
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ModelAerodynamicPartsPanel(AerodynamicPartKind.CLEAN, parts) } } }
        compose.onNodeWithText("显示 8 / 9 个来源").assertExists()
        compose.onNodeWithText("来源：WingPlaneSweep8.NoFlaps").assertDoesNotExist()
        compose.onNodeWithTag("aero-parts-more-CLEAN").performScrollTo().performClick()
        compose.onNodeWithText("来源：WingPlaneSweep8.NoFlaps").assertExists()
        compose.onNodeWithText("显示 9 / 9 个来源").assertExists()
        compose.runOnIdle { parts = parts.map { it.copy(cdMin = 0.1) } }
        compose.onNodeWithText("显示 8 / 9 个来源").assertExists()
        compose.onNodeWithText("来源：WingPlaneSweep8.NoFlaps").assertDoesNotExist()
    }

}
