package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*
import voidmei.fm.FlightModelParameters

class ModelComparisonExportGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportUsesSelectedConditionsAndProtectsExistingFile() {
        val directory = Files.createTempDirectory("model-comparison-export")
        val destination = directory.resolve("comparison.csv")
        var selected: String? = null
        val baseline = NamedModel("left", FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList()))
        val current = baseline.copy(aircraft = "right", parameters = baseline.parameters.copy(maximumFuelMassKg = 300.0))
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                ModelComparisonPanel(baseline, current, chooseExport = { selected })
            } } }
            compose.onNodeWithText("导出参数比较 CSV").performScrollTo().performClick()
            compose.waitForIdle()
            assertFalse(Files.exists(destination))
            compose.onNodeWithContentDescription("比较燃油比例")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
            compose.runOnIdle { selected = destination.toString() }
            compose.onNodeWithText("导出参数比较 CSV").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("比较已导出：$destination").fetchSemanticsNodes().isNotEmpty() }
            val saved = Files.readString(destination)
            assertTrue(saved.lines().first { it.startsWith("\"比较燃油量\"") }
                .contains("\"left\",\"right\",\"50.0\",\"150.0\",\"100.0\",\"0.0\",\"0.0\",\"0.5\""))
            compose.onNodeWithContentDescription("比较燃油比例")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
            compose.onNodeWithText("导出参数比较 CSV").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("目标文件已存在，请选择新文件名。").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(saved, Files.readString(destination))
        } finally {
            Files.deleteIfExists(destination)
            Files.deleteIfExists(directory)
        }
    }
}
