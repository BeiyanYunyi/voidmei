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
import voidmei.fm.JetThrustModel
import kotlin.test.*

class JetAltitudeComparisonGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun comparesAltitudeRowsAndNeverFillsMissingOrAfterburnerValues() {
        var model by mutableStateOf(JetThrustModel("Engine0", listOf(0.0, 1000.0), listOf(0.0, 500.0, 1000.0),
            listOf(listOf(1000.0, null, 3000.0), listOf(800.0, 1600.0, 2400.0)),
            listOf(listOf(1500.0, null, 4500.0), listOf(1200.0, 2400.0, 3600.0))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JetThrustCurvePanel(model) } } }
        compose.onNodeWithText("展开多高度推力对比").performScrollTo().performClick()
        compose.onNodeWithText("高度 0.00 m：1500.00 kgf/台").assertExists()
        compose.onNodeWithText("高度 1000.00 m：1200.00 kgf/台").assertExists()
        compose.onNodeWithTag("jet-altitudes-speed").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithText("高度 0.00 m：— kgf/台").assertExists()
        compose.onNodeWithText("高度 1000.00 m：2400.00 kgf/台").assertExists()
        val pixels = compose.onNodeWithTag("jet-altitudes-plot").performScrollTo().captureToImage().toPixelMap()
        var orange = 0
        var interiorGreen = 0
        for (y in 0 until pixels.height) for (x in pixels.width / 5 until pixels.width * 4 / 5) {
            val c = pixels[x, y]
            if (c.red > .85f && c.green > .5f && c.blue < .5f) orange++
            if (c.green > .7f && c.green > c.red + .15f && c.blue > .6f) interiorGreen++
        }
        assertTrue(orange > 10); assertEquals(0, interiorGreen, "Missing middle cell must break the green curve")
        compose.onNodeWithText("多高度军用").performScrollTo().performClick()
        compose.onNodeWithText("高度 1000.00 m：1600.00 kgf/台").assertExists()
        compose.runOnIdle { model = model.copy(afterburnerKgf = null) }
        compose.onNodeWithText("展开多高度推力对比").performScrollTo().performClick()
        compose.onNodeWithText("多高度加力").performScrollTo().performClick()
        compose.onNodeWithText("模型未提供加力推力表，不使用军用数据替代。").assertExists()
        compose.onNodeWithText("高度 1000.00 m：— kgf/台").assertExists()
    }
    @Test fun allAltitudeGroupsAreReachableAndResetWithNewModel() {
        var model by mutableStateOf(JetThrustModel("Engine0", (0..7).map { it * 1000.0 }, listOf(0.0),
            (0..7).map { listOf(1000.0 - it * 10) }, null))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JetAltitudeComparisonPanel(model) } } }
        compose.onNodeWithText("展开多高度推力对比").performClick()
        compose.onNodeWithText("高度 5000.00 m：950.00 kgf/台").assertExists()
        compose.onNodeWithTag("jet-altitudes-next").performScrollTo().performClick()
        compose.onNodeWithText("高度 7000.00 m：930.00 kgf/台").assertExists()
        compose.onNodeWithText("高度 0.00 m：1000.00 kgf/台").assertDoesNotExist()
        compose.onNodeWithTag("jet-altitudes-next").assertIsNotEnabled()
        compose.runOnIdle { model = model.copy(source = "Engine1") }
        compose.onNodeWithText("展开多高度推力对比").performScrollTo().performClick()
        compose.onNodeWithText("高度 0.00 m：1000.00 kgf/台").assertExists()
        compose.onNodeWithTag("jet-altitudes-previous").assertIsNotEnabled()
    }
}
