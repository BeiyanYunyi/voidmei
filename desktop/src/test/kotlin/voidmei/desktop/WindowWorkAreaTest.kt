package voidmei.desktop

import java.awt.Insets
import java.awt.Rectangle
import voidmei.config.WindowPosition
import kotlin.test.*

class WindowWorkAreaTest {
    @Test fun reservedPanelsCannotCoverTheRestoredTitleArea() {
        val area = assertNotNull(windowWorkArea(Rectangle(0, 0, 1920, 1080), Insets(40, 80, 30, 0)))
        assertEquals(Rectangle(80, 40, 1840, 1010), area)
        for (point in listOf(WindowPosition(0f, 50f), WindowPosition(100f, 0f), WindowPosition(100f, 1030f)))
            assertNull(visiblePosition(point, listOf(area)))
        val point = WindowPosition(80f, 40f)
        assertEquals(point, visiblePosition(point, listOf(area)))
    }

    @Test fun negativeMonitorsAndInvalidWorkAreasRemainExplicit() {
        val area = assertNotNull(windowWorkArea(Rectangle(-1920, -100, 1920, 1080), Insets(40, 0, 0, 0)))
        val point = WindowPosition(-1800f, -50f)
        assertEquals(point, visiblePosition(point, listOf(area)))
        assertNull(windowWorkArea(Rectangle(0, 0, 100, 100), Insets(100, 0, 0, 0)))
        assertNull(windowWorkArea(Rectangle(0, 0, 100, 100), Insets(-1, 0, 0, 0)))
        assertNull(windowWorkArea(Rectangle(Int.MAX_VALUE, 0, 100, 100), Insets(0, 1, 0, 0)))
    }
}
