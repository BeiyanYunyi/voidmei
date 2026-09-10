package voidmei.desktop

import kotlin.test.*

class HudWorkAreaTest {
    @Test fun preferredWidthFitsWorkAreaAndAccountsForDpiAndTaskbars() {
        val pixels = hudAvailableWidthPixels(800, 20, 40, 2.0)
        assertEquals(1480.0, pixels)
        assertEquals(740f, hudWidthDp(1000, pixels, 2f))
        assertEquals(440f, hudWidthDp(440, pixels, 2f))
        assertEquals(160f, hudWidthDp(240, 320.0, 2f))
        assertEquals(680f, hudWidthDp(680, null, 1f))
        assertEquals(1f, hudWidthDp(240, 0.5, 1f))
        assertNull(hudAvailableWidthPixels(800, 400, 400, 1.0))
        assertNull(hudAvailableWidthPixels(800, -1, 0, 1.0))
        assertNull(hudAvailableWidthPixels(800, 0, 0, Double.NaN))
    }

    @Test fun originRepairKeepsHeaderAccessibleAcrossWorkAreaEdges() {
        val bounds = java.awt.Rectangle(-1280, -800, 1280, 800)
        val insets = java.awt.Insets(20, 10, 40, 10)
        assertEquals(java.awt.Point(-450, -160), hudSafeOrigin(bounds, insets, 20, 30, 440, 120))
        assertEquals(java.awt.Point(-1270, -780), hudSafeOrigin(bounds, insets, -2000, -1000, 440, 120))
        assertEquals(java.awt.Point(-800, -500), hudSafeOrigin(bounds, insets, -800, -500, 440, 120))
        assertEquals(java.awt.Point(0, 0), hudSafeOrigin(java.awt.Rectangle(0, 0, 80, 60),
            java.awt.Insets(0, 0, 0, 0), 100, 100, 440, 120))
        assertNull(hudSafeOrigin(bounds, java.awt.Insets(500, 0, 500, 0), 0, 0, 440, 120))
    }

    @Test fun taskbarsWindowOriginAndNegativeMonitorCoordinatesReduceAvailableSpace() {
        assertEquals(640.0, hudAvailableHeightPixels(0, 800, 20, 40, 120, 1.0))
        assertEquals(1480.0, hudAvailableHeightPixels(-800, 800, 20, 40, -800, 2.0))
        assertEquals(120.0, hudAvailableHeightPixels(-800, 800, 20, 40, -100, 2.0))
        assertNull(hudAvailableHeightPixels(0, 800, 400, 400, 0, 1.0))
        assertNull(hudAvailableHeightPixels(0, 800, 0, 0, 0, Double.NaN))
    }

    @Test fun physicalPixelsConvertToComposeDpWithoutDoubleApplyingDpi() {
        val pixels = hudAvailableHeightPixels(0, 800, 0, 40, 100, 2.0)
        assertEquals(660f, hudHeightDp(1000f, pixels, 2f))
        assertEquals(440f, hudHeightDp(1000f, pixels, 3f))
        assertEquals(200f, hudHeightDp(200f, pixels, 2f))
        assertEquals(80f, hudHeightDp(200f, 160.0, 2f))
        assertEquals(900f, hudHeightDp(1000f, null, 1f))
        assertEquals(120f, hudHeightDp(Float.NaN, null, 1f))
    }
}
