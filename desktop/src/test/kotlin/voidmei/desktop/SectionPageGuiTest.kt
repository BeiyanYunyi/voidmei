package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SectionPageGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedNavigationFindsSectionsWithoutDestroyingDrafts() {
        compose.setContent { MaterialTheme { SectionPage { anchors ->
            MainSection.entries.forEach { section ->
                SectionHeading(section, anchors)
                if (section == MainSection.SETTINGS) {
                    var draft by remember { mutableStateOf("") }
                    OutlinedTextField(draft, { draft = it }, label = { Text("保留草稿") })
                }
                Spacer(Modifier.height(800.dp))
            }
        } } }
        compose.onNodeWithText("保留草稿").performTextInput("unfinished endpoint")
        compose.onNodeWithTag("section-nav-MAP").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-MAP").assertIsDisplayed()
        compose.onNodeWithTag("section-nav-SETTINGS").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("保留草稿").assertTextContains("unfinished endpoint")
        compose.onNodeWithTag("section-nav-MODEL").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-MODEL").assertIsDisplayed()
    }
}
