package voidmei.fm

import kotlin.test.*

class FlapLimitsTest {
    private fun extract(text: String) = FlapLimitExtractor.extract(BlkParser.parse(text))
    @Test fun indexedTableInterpolatesInBothDirectionsAndSupportsMoreThanFiveStages() {
        val result = extract((0..5).reversed().joinToString("\n") {
            "FlapsDestructionIndSpeedP$it:p2=${(it + 1) / 6.0},${600 - it * 60}"
        })
        val table = result.limits!!
        assertTrue(result.issues.isEmpty())
        assertEquals(6, table.points.size)
        assertEquals(570.0, table.speedAt(25.0))
        assertEquals(25.0, table.maximumPercentAt(570.0))
        assertEquals(100.0, table.maximumPercentAt(0.0))
        assertNull(table.maximumPercentAt(601.0))
        assertNull(table.speedAt(1.0))
        assertNull(table.speedAt(0.0))
    }
    @Test fun legacyPairAndScalarFormsAreReadable() {
        val pair = extract("FlapsDestructionIndSpeedP:p4=0.5,500,1,300").limits!!
        assertEquals(400.0, pair.speedAt(75.0))
        assertEquals(75.0, pair.maximumPercentAt(400.0))
        val scalar = extract("FlapsDestructionIndSpeed:r=300").limits!!
        assertEquals(300.0, scalar.speedAt(100.0))
        assertNull(scalar.speedAt(50.0))
        val flat = FlapLimits(listOf(FlapLimitPoint(.5, 300.0), FlapLimitPoint(1.0, 300.0)))
        assertEquals(100.0, flat.maximumPercentAt(300.0))
        assertEquals(300.0, flat.speedAt(75.0))
        val partial = FlapLimits(listOf(FlapLimitPoint(.25, 600.0), FlapLimitPoint(.75, 300.0)))
        assertFalse(partial.isNearLimit(90.0, 400.0, 8.0))
    }
    @Test fun invalidAndAmbiguousTablesRemainUnknown() {
        for (text in listOf("FlapsDestructionIndSpeed:r=0", "FlapsDestructionIndSpeedP0:p2=1.25,300",
            "FlapsDestructionIndSpeedP0:p2=0.5,300\nFlapsDestructionIndSpeedP1:p2=0.5,200",
            "FlapsDestructionIndSpeedP0:p2=0.5,300\nFlapsDestructionIndSpeedP1:p2=1,400",
            "A { FlapsDestructionIndSpeed:r=300 } B { FlapsDestructionIndSpeed:r=400 }")) {
            val result = extract(text)
            assertNull(result.limits)
            assertTrue(result.issues.isNotEmpty())
        }
        assertNull(extract("EmptyMass:r=1000").limits)
        assertEquals(300.0, FlightModelExtractor.extract(BlkParser.parse("FlapsDestructionIndSpeed:r=300")).flapLimits?.speedAt(100.0))
    }
}
