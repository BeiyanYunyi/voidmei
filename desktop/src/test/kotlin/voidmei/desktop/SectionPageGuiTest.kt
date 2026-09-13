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
        compose.setContent { MaterialTheme { SectionPage { section ->
            if (section == MainSection.SETTINGS) {
                var draft by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
                OutlinedTextField(draft, { draft = it }, label = { Text("保留草稿") })
            }
            Spacer(Modifier.height(800.dp))
        } } }
        compose.onNodeWithText("保留草稿").performTextInput("unfinished endpoint")
        compose.onNodeWithTag("section-nav-MAP").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-MAP").assertIsDisplayed()
        compose.onNodeWithTag("section-SETTINGS").assertDoesNotExist()
        compose.onNodeWithText("保留草稿").assertDoesNotExist()
        compose.onNodeWithTag("section-nav-SETTINGS").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("保留草稿").assertTextContains("unfinished endpoint")
        compose.onNodeWithTag("section-nav-MODEL").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section-MODEL").assertIsDisplayed()
    }

    @Test fun categoriesShowOnlyTheSelectedPageAndRestoreNestedDrafts() {
        compose.setContent { MaterialTheme { SectionPage { section ->
            if (section == MainSection.SETTINGS) SettingsPages("设置",
                SettingsDestination("display", "显示", "字体与颜色") {
                    SettingsPages("显示",
                        SettingsDestination("font", "字体", "输入字体名称") {
                            var draft by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
                            OutlinedTextField(draft, { draft = it }, label = { Text("字体草稿") })
                        })
                },
                SettingsDestination("network", "连接", "遥测服务器") { Text("服务器配置") })
        } } }
        compose.onNodeWithText("字体草稿").assertDoesNotExist()
        compose.onNodeWithText("服务器配置").assertDoesNotExist()
        compose.onNodeWithTag("settings-open-display").performClick()
        compose.onNodeWithTag("settings-open-network").assertDoesNotExist()
        compose.onNodeWithTag("settings-open-font").performClick()
        compose.onNodeWithText("字体草稿").performTextInput("unfinished font")
        compose.onNodeWithTag("settings-back-显示").performClick()
        compose.onNodeWithText("字体草稿").assertDoesNotExist()
        compose.onNodeWithTag("settings-back-设置").performClick()
        compose.onNodeWithTag("settings-open-network").performClick()
        compose.onNodeWithText("服务器配置").assertIsDisplayed()
        compose.onNodeWithTag("settings-back-设置").performClick()
        compose.onNodeWithTag("settings-open-display").performClick()
        compose.onNodeWithTag("settings-open-font").performClick()
        compose.onNodeWithText("字体草稿").assertTextContains("unfinished font")
        compose.onNodeWithTag("section-nav-MAP").performClick()
        compose.onNodeWithText("字体草稿").assertDoesNotExist()
        compose.onNodeWithTag("section-nav-SETTINGS").performClick()
        compose.onNodeWithText("字体草稿").assertTextContains("unfinished font")
    }

}
