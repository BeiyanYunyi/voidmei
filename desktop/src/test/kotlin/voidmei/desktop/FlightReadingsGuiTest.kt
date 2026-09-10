package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class FlightReadingsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readingsReflowWithoutClippingLongLabelsOrValues() {
        var width by mutableStateOf(440.dp)
        var fontScale by mutableStateOf(1f)
        var rows by mutableStateOf(listOf("IAS" to "321 km/h", "高度" to "1234 m"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme { Box(Modifier.width(width).testTag("readings")) { FlightReadings(rows, true) } }
            }
        }
        assertEquals(compose.onNodeWithText("IAS").getUnclippedBoundsInRoot().top,
            compose.onNodeWithText("高度").getUnclippedBoundsInRoot().top)
        compose.runOnIdle {
            width = 220.dp
            fontScale = 2f
            rows = listOf("雷达高度原值" to "123456 仪表单位", "滚转角速度" to "-1234.5 °/s")
        }
        fun checkVisibleText() {
            val bounds = compose.onNodeWithTag("readings").getUnclippedBoundsInRoot()
            rows.flatMap { listOf(it.first, it.second) }.forEach { text ->
                val node = compose.onNodeWithText(text)
                node.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { layout ->
                    // Desktop semantics can retain a wider paragraph constraint than the text node.
                    // Check the actual lines, including their last character, instead of empty paragraph space.
                    assertFalse(layout.didOverflowHeight, "Clipped height: $text")
                    assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
                    repeat(layout.lineCount) { line ->
                        assertFalse(layout.isLineEllipsized(line))
                        assertTrue(layout.getLineLeft(line) >= -1f)
                        assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, "Clipped line: $text")
                    }
                }
                val box = node.getUnclippedBoundsInRoot()
                assertTrue(box.left >= bounds.left && box.right <= bounds.right, "$text exceeds $bounds")
            }
        }
        checkVisibleText()
        val label = compose.onNodeWithText("雷达高度原值").getUnclippedBoundsInRoot()
        val value = compose.onNodeWithText("123456 仪表单位").getUnclippedBoundsInRoot()
        assertTrue(value.top >= label.bottom)
        compose.runOnIdle { width = 800.dp; fontScale = 1f }
        checkVisibleText()
        assertEquals(compose.onNodeWithText("雷达高度原值").getUnclippedBoundsInRoot().top,
            compose.onNodeWithText("滚转角速度").getUnclippedBoundsInRoot().top)
    }
}
