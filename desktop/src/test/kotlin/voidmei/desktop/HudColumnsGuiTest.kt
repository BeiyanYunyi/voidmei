package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class HudColumnsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedColumnsKeepOrderAndWrapLongReadingsWithinTheirCell() {
        var columns by mutableStateOf(1)
        var speed by mutableStateOf("300 km/h")
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalReadingColumns provides columns) {
                Box(Modifier.width(300.dp)) {
                    FlightReadings(listOf("IAS" to speed, "SEP" to "12 m/s", "高度" to "1000 m"), compact = true)
                }
            }
        } }
        fun bounds(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds("SEP").top > bounds("IAS").top)
        compose.runOnIdle { columns = 2 }
        assertEquals(bounds("IAS").top, bounds("SEP").top)
        assertTrue(bounds("SEP").left > bounds("IAS").left)
        assertTrue(bounds("高度").top > bounds("IAS").top)
        compose.runOnIdle { speed = "123456789.123 km/h" }
        assertTrue(bounds(speed).top >= bounds("IAS").bottom)
        assertTrue(bounds(speed).right <= bounds("SEP").left)
        assertTrue(bounds("高度").top >= bounds(speed).bottom)
        compose.runOnIdle { speed = "1 km/h" }
        assertEquals(bounds("IAS").top, bounds("SEP").top)
    }
}
