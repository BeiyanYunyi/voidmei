package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelFlapLimitsTableGuiTest {
    @get:Rule val compose = createComposeRule()
    private fun extract(text: String) = FlapLimitExtractor.extract(BlkParser.parse(text)).limits
    @Test fun rendersSortedRealPointsAndClearsRowsForInvalidOrMissingData() {
        var limits by mutableStateOf(extract("FlapsDestructionIndSpeedP1:p2=1,300\nFlapsDestructionIndSpeedP0:p2=0.25,550.125"))
        compose.setContent { MaterialTheme { Column { ModelFlapLimitsTable(limits) } } }
        compose.onNodeWithTag("model-flap-limit-row-0").onChildren().filterToOne(hasText("25.00")).assertExists()
        compose.onNodeWithTag("model-flap-limit-row-0").onChildren().filterToOne(hasText("550.13")).assertExists()
        compose.onNodeWithTag("model-flap-limit-row-1").onChildren().filterToOne(hasText("100.00")).assertExists()
        compose.onNodeWithText("显示 2 / 2 个数据点").assertExists()
        compose.runOnIdle { limits = extract("FlapsDestructionIndSpeed:r=270") }
        compose.onNodeWithText("显示 1 / 1 个数据点").assertExists()
        compose.onNodeWithText("270.00").assertExists()
        compose.onNodeWithTag("model-flap-limit-row-1").assertDoesNotExist()
        for (source in listOf("FlapsDestructionIndSpeed:r=-1", "Mass { EmptyMass:r=2500 }")) {
            compose.runOnIdle { limits = extract(source) }
            compose.onNodeWithTag("model-flap-limit-table").assertDoesNotExist()
            compose.onNodeWithText("模型未提供有效襟翼限速数据点").assertExists()
        }
    }
    @Test fun exposesAll64PointsAndResetsPaginationWhenModelChanges() {
        var limits by mutableStateOf(FlapLimits((1..64).map { FlapLimitPoint(it / 64.0, 700.0 - it) }))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ModelFlapLimitsTable(limits) } } }
        compose.onNodeWithText("显示 8 / 64 个数据点").assertExists()
        repeat(7) { compose.onNodeWithTag("model-flap-limits-more").performScrollTo().performClick() }
        compose.onNodeWithTag("model-flap-limit-row-63").performScrollTo().onChildren().filterToOne(hasText("100.00")).assertExists()
        compose.onNodeWithText("显示 64 / 64 个数据点").assertExists()
        compose.onNodeWithTag("model-flap-limits-more").assertDoesNotExist()
        compose.runOnIdle { limits = FlapLimits((1..16).map { FlapLimitPoint(it / 16.0, 500.0 - it) }) }
        compose.onNodeWithText("显示 8 / 16 个数据点").assertExists()
        compose.onNodeWithTag("model-flap-limit-row-8").assertDoesNotExist()
    }
}
