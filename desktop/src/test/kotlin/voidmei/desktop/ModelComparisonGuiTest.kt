package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*

class ModelComparisonGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedFuelControlUpdatesBothMassesAndHandlesUnknownCapacity() {
        val baseline = NamedModel("left", FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList(),
            stallSpeed = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 20.0, 40.0)))))
        val right = NamedModel("right", baseline.parameters.copy(maximumFuelMassKg = 300.0))
        var current by mutableStateOf<NamedModel?>(right)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelComparisonPanel(baseline, current)
        } } }
        compose.onNodeWithContentDescription("比较燃油比例").performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        compose.onNodeWithTag("model-comparison-比较燃油量").assertTextContains("50.00")
            .assertTextContains("150.00").assertTextContains("100.00")
        compose.onNodeWithContentDescription("比较后掠位置").performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        compose.onNodeWithContentDescription("比较襟翼开度").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.runOnIdle { current = null }
        compose.onNodeWithText("等待当前机型模型，比较基准已保留。").assertExists()
        compose.onNodeWithTag("model-comparison-比较燃油量").assertDoesNotExist()
        compose.onNodeWithText("共同条件：后掠 25% · 襟翼 75% · 燃油 50%").assertExists()
        compose.runOnIdle { current = right.copy(aircraft = "next") }
        compose.onNodeWithText("共同条件：后掠 25% · 襟翼 75% · 燃油 50%").assertExists()
        compose.onNodeWithTag("model-comparison-比较燃油量").assertTextContains("150.00")
        compose.runOnIdle { current = right.copy(parameters = right.parameters.copy(maximumFuelMassKg = null)) }
        compose.onNodeWithTag("model-comparison-比较燃油量").assertTextContains("50.00").assertTextContains("—")
        compose.onNodeWithTag("model-comparison-1 G 失速 IAS 估算").assertTextContains("—")
        compose.onNodeWithContentDescription("比较燃油比例").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("model-comparison-1 G 失速 IAS 估算").assertTextContains("0.00")
    }
}
