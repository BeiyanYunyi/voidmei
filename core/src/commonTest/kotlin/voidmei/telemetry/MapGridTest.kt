package voidmei.telemetry

import kotlin.test.*

class MapGridTest {
    private val bounds = MapBounds(MapPoint(-1000.0, -1000.0), MapPoint(1000.0, 1000.0), 1,
        MapPoint(200.0, 200.0), MapPoint(-1000.0, 1000.0))
    private fun snapshot(x: Double, y: Double, area: MapBounds = bounds) = MapSnapshot(area,
        listOf(MapObject(null, "Player", null, MapPoint(x, y), null, null, null, null)))

    @Test fun gridLinesUseMetadataAndBoundDrawingWork() {
        val lines = assertNotNull(MapGrid.lines(bounds))
        assertEquals(11, lines.vertical.size)
        assertEquals(lines.vertical, lines.horizontal)
        assertEquals(.3, lines.vertical[3], 1e-12)
        val shifted = assertNotNull(MapGrid.lines(bounds.copy(gridZero = MapPoint(-900.0, 900.0),
            gridSteps = MapPoint(400.0, 200.0))))
        assertEquals(.05, shifted.vertical.first(), 1e-12)
        assertEquals(.25, shifted.vertical[1], 1e-12)
        assertEquals(.05, shifted.horizontal.first(), 1e-12)
        assertEquals(.15, shifted.horizontal[1], 1e-12)
        assertTrue(shifted.vertical.all { it in 0.0..1.0 })
        assertNull(MapGrid.lines(bounds.copy(gridZero = null)))
        for (step in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.001))
            assertNull(MapGrid.lines(bounds.copy(gridSteps = MapPoint(step, 200.0))))
        assertNull(MapGrid.lines(bounds.copy(gridZero = MapPoint(Double.MAX_VALUE, 1000.0))))
    }

    @Test fun wholeCellOriginShiftsKeepBoundaryLines() {
        val expected = assertNotNull(MapGrid.lines(bounds))
        for (shift in listOf(-600.0, 600.0, -1400.0, 1400.0)) {
            val shifted = assertNotNull(MapGrid.lines(bounds.copy(
                gridZero = MapPoint(-1000.0 + shift, 1000.0 - shift))))
            assertEquals(expected.vertical.size, shifted.vertical.size, "shift $shift must preserve boundary lines")
            assertEquals(0.0, shifted.vertical.first(), "shift $shift must start at the map edge")
            assertEquals(1.0, shifted.vertical.last())
            expected.vertical.zip(shifted.vertical).forEach { (a, b) -> assertEquals(a, b, 1e-12) }
            assertEquals(shifted.vertical, shifted.horizontal)
        }
    }

    @Test fun cellsMatchLegacySquareGridAndRespectIndependentAxes() {
        assertEquals("A1", MapGrid.playerCell(snapshot(0.0, 0.0)))
        assertEquals("C4", MapGrid.playerCell(snapshot(.35, .25)))
        assertEquals("J10", MapGrid.playerCell(snapshot(.999, .999)))
        assertEquals("C3", MapGrid.playerCell(snapshot(.35, .25, bounds.copy(
            gridSteps = MapPoint(400.0, 200.0), gridZero = MapPoint(-1400.0, 1000.0)))))
        assertEquals("D4", MapGrid.playerCell(snapshot(.35, .25, bounds.copy(
            gridZero = MapPoint(-1000.0, 1200.0)))))
        assertEquals("B2", MapGrid.playerCell(snapshot(.1, .1)))
    }

    @Test fun unknownAndInvalidInputsNeverProducePlausibleCells() {
        val sample = snapshot(.35, .25)
        assertNull(MapGrid.playerCell(sample.copy(objects = emptyList())))
        assertNull(MapGrid.playerCell(sample.copy(objects = sample.objects + sample.objects)))
        assertNull(MapGrid.playerCell(sample.copy(bounds = bounds.copy(gridZero = null))))
        assertNull(MapGrid.playerCell(sample.copy(bounds = bounds.copy(gridSteps = null))))
        for (bad in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(MapGrid.playerCell(snapshot(.35, .25, bounds.copy(gridSteps = MapPoint(bad, 200.0)))))
            assertNull(MapGrid.playerCell(snapshot(.35, .25, bounds.copy(gridSteps = MapPoint(200.0, bad)))))
        }
        for (bad in listOf(-.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(MapGrid.playerCell(snapshot(bad, .25)))
            assertNull(MapGrid.playerCell(snapshot(.35, bad)))
        }
        assertNull(MapGrid.playerCell(snapshot(.35, .25, bounds.copy(gridZero = MapPoint(1000.0, 1000.0)))))
        assertNull(MapGrid.playerCell(snapshot(.35, .25, bounds.copy(gridSteps = MapPoint(200.0, 1.0)))))
    }
}
