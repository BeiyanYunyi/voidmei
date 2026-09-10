package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.BlkField

class ModelFieldsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filteringPreservesDuplicatesClearsStaleResultsAndResetsScroll() {
        var fields by mutableStateOf((0 until 100).map { "Engine.Power$it" to BlkField("Power$it", "r", listOf("$it")) } +
            listOf("Mass.EmptyMass" to BlkField("EmptyMass", "r", listOf("2500")),
                "Mass.EmptyMass" to BlkField("EmptyMass", "r", listOf("2600"))))
        var filter by mutableStateOf("")
        compose.setContent { MaterialTheme { Column { ModelFieldsPanel(fields, filter) { filter = it } } } }
        compose.onNodeWithTag("model-field-list").performScrollToIndex(99)
        compose.onNodeWithText("筛选字段路径").performTextReplacement("mass")
        compose.waitUntil(5000) { compose.onAllNodesWithText("匹配 2 / 102 个字段").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mass.EmptyMass : r = 2500").assertIsDisplayed()
        compose.onNodeWithText("Mass.EmptyMass : r = 2600").assertIsDisplayed()
        compose.runOnIdle { fields = listOf("New.Field" to BlkField("Field", "i", listOf("7"))) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("没有匹配的字段路径").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mass.EmptyMass : r = 2500").assertDoesNotExist()
        compose.onNodeWithText("筛选字段路径").performTextClearance()
        compose.onNodeWithText("匹配 1 / 1 个字段").assertExists()
        compose.onNodeWithText("New.Field : i = 7").assertIsDisplayed()
    }
}
