package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import voidmei.fm.*

class JetComparisonGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun comparesAtSharedCoordinatesWithoutExtrapolationOrAfterburnerFallback() {
        val parameters = FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList())
        val left = NamedModel("left", parameters, listOf(JetThrustModel("Engine0", listOf(0.0, 1000.0),
            listOf(0.0, 1000.0), listOf(listOf(1000.0, 2000.0), listOf(800.0, 1600.0)),
            listOf(listOf(1500.0, 3000.0), listOf(1200.0, 2400.0)))))
        val right = NamedModel("right", parameters, listOf(JetThrustModel("Engine1", listOf(500.0, 1500.0),
            listOf(500.0, 1500.0), listOf(listOf(1800.0, 2600.0), listOf(1000.0, 1800.0)), null)))
        var current by mutableStateOf<NamedModel?>(right)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelComparisonPanel(left, current)
        } } }
        compose.onNodeWithText("展开推力叠加比较").performScrollTo().performClick()
        compose.onNodeWithText("TAS 0.00 km/h · 基准 1000.00 kgf · 当前 — kgf").assertExists()
        compose.onNodeWithContentDescription("推力比较高度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithContentDescription("推力比较速度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithText("TAS 750.00 km/h · 基准 1487.50 kgf · 当前 1800.00 kgf").assertExists()
        val pixels = compose.onNodeWithTag("jet-comparison-plot").performScrollTo().captureToImage().toPixelMap()
        var green = 0
        var orange = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.green > 0.7f && color.green > color.red + 0.15f && color.blue > 0.6f) green++
            if (color.red > 0.85f && color.green > 0.5f && color.blue < 0.5f) orange++
        }
        assertTrue(green > 10 && orange > 10)
        compose.onNodeWithContentDescription("推力比较速度").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithText("TAS 1500.00 km/h · 基准 — kgf · 当前 2400.00 kgf").assertExists()
        compose.onNodeWithTag("jet-comparison-plot").performScrollTo().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width / 2f, height / 2f))
        }
        compose.onNodeWithText("TAS 750.00 km/h · 基准 1487.50 kgf · 当前 1800.00 kgf").assertExists()
        compose.onNodeWithContentDescription("推力比较速度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithText("加力推力比较").performScrollTo().performClick()
        compose.onNodeWithText("TAS 750.00 km/h · 基准 2231.25 kgf · 当前 — kgf").assertExists()
        compose.onNodeWithText("当前在此高度/模式无有效推力数据").assertExists()
        compose.runOnIdle { current = null }
        compose.onNodeWithTag("jet-comparison-plot").assertDoesNotExist()
        compose.onNodeWithText("等待当前模型，推力比较条件已保留。").assertExists()
        compose.runOnIdle {
            current = right.copy(jets = listOf(right.jets.single().copy(
                altitudesM = listOf(500.0, 2000.0), velocitiesKmh = listOf(500.0, 2000.0))))
        }
        compose.onNodeWithText("共同高度 750.00 m").assertExists()
        compose.onNodeWithText("TAS 750.00 km/h · 基准 2231.25 kgf · 当前 — kgf").assertExists()
        compose.onNodeWithText("收起推力叠加比较").performScrollTo().performClick()
        compose.onNodeWithTag("jet-comparison-plot").assertDoesNotExist()
        compose.onNodeWithText("展开推力叠加比较").performScrollTo().performClick()
        compose.onNodeWithText("TAS 750.00 km/h · 基准 2231.25 kgf · 当前 — kgf").assertExists()

    }
}
