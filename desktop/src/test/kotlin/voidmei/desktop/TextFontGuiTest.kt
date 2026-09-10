package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class TextFontGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appliesThemeFontAndFallsBackWithoutLosingMissingName() {
        var name by mutableStateOf<String?>(null)
        var family: FontFamily? = null
        compose.setContent {
            val typography = remember(name) { textTypography(resolveTextFont(name).family) }
            MaterialTheme(typography = typography) { Column {
                family = MaterialTheme.typography.bodyLarge.fontFamily
                TextFontSettings(name) { name = it }
            } }
        }
        compose.onNodeWithText("全局文字字体").performTextReplacement("serif")
        compose.runOnIdle { assertNull(name) }
        compose.onNodeWithText("应用文字字体").performClick()
        compose.runOnIdle { assertEquals(FontFamily.Serif, family) }
        compose.onNodeWithText("全局文字字体").performTextReplacement("voidmei-missing-font-476398")
        compose.onNodeWithText("应用文字字体").performClick()
        compose.onNodeWithText("未找到文字字体", substring = true).assertExists()
        compose.runOnIdle { assertEquals("voidmei-missing-font-476398", name); assertEquals(FontFamily.Default, family) }
        compose.onNodeWithText("全局文字字体").performTextReplacement("")
        compose.onNodeWithText("应用文字字体").performClick()
        compose.runOnIdle { assertNull(name); assertEquals(FontFamily.Default, family) }
    }
}
