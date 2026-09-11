package voidmei.desktop

import java.awt.Rectangle
import kotlin.test.*

class HudDisplayPlacementTest {
    @Test fun displaySelectionKeepsExplicitTargetAndFallsBackToPrimary() {
        val left = HudDisplay("left", Rectangle(-1920, 0, 1920, 1080), false)
        val main = HudDisplay("main", Rectangle(0, 0, 2560, 1440), true)
        assertEquals(left, selectHudDisplay(listOf(main, left), "left"))
        assertEquals(main, selectHudDisplay(listOf(left, main), "disconnected"))
        assertEquals(left, selectHudDisplay(listOf(left), "disconnected"))
        assertNull(selectHudDisplay(emptyList(), "disconnected"))
    }

    @Test fun negativeOriginsAndNonuniformScaleFollowHudBridgeCoordinates() {
        val (position, size) = hudDisplayState(Rectangle(-1920, 100, 1920, 1080), 1.5, 2.0, 2f)
        assertEquals(-1440f, position.x.value)
        assertEquals(100f, position.y.value)
        assertEquals(1440f, size.width.value)
        assertEquals(1080f, size.height.value)
        assertFailsWith<IllegalArgumentException> { hudDisplayState(Rectangle(0, 0, 10, 10), 1.0, 1.0, 0f) }
    }
}
