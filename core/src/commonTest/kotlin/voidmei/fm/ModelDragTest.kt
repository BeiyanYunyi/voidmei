package voidmei.fm

import kotlin.math.PI
import kotlin.test.*

class ModelDragTest {
    private val areas = listOf("LeftIn", "LeftMid", "LeftOut", "LeftCut", "RightIn", "RightMid", "RightOut", "RightCut", "Aileron")
        .joinToString("; ") { "$it:r=1" }
    private fun model(extra: String = "") = FlightModelExtractor.extract(BlkParser.parse("""
        Mass { EmptyMass:r=1000; OilMass:r=20; MaxNitro:r=30; MaxFuelMass0:r=100 }
        OswaldsEfficiencyNumber:r=0.8
        FuselagePlane { Areas { Main:r=2 }; Polar { CdMin:r=0.05 } }
        WingPlaneSweep0 { Sweep:r=0; Span:r=6; Areas { $areas }; NoFlaps { CdMin:r=0.1 } $extra }
        WingPlaneSweep1 { Sweep:r=1; Span:r=3; Areas { $areas }; NoFlaps { CdMin:r=0.2 } }
    """))
    @Test fun alignsDragAreasAndInducedFactorsWithEachWingAndHalfFuelMass() {
        val refs = model().dragReferences()
        assertEquals(2, refs.size)
        assertEquals(1.0, refs[0].dragArea!!, 1e-10)
        assertEquals(1.9, refs[1].dragArea!!, 1e-10)
        assertEquals(1 / (PI * 4 * .8), refs[0].inducedFactor!!, 1e-10)
        assertEquals(refs[0].inducedFactor!! * 4, refs[1].inducedFactor!!, 1e-10)
        assertEquals(1100.0, refs[0].halfFuelMassKg)
        assertEquals(1 / 1.1, refs[0].areaPerTonne!!, 1e-10)
        assertEquals(1100 / (PI * 4 * .8), refs[0].massTimesInducedFactor!!, 1e-10)
        assertEquals("WingPlaneSweep1.NoFlaps", refs[1].cleanSource)
        assertEquals("FuselagePlane.Polar", refs[1].bodySource)
    }
    @Test fun ambiguityAndMissingDataDoNotBorrowOtherProfilesOrInventZero() {
        assertNull(model("FlapsPolar0 { CdMin:r=0.3 }").dragReferences()[0].dragArea)
        val original = model()
        val missing = original.copy(aerodynamicParts = original.aerodynamicParts.filterNot { it.sourcePath == "WingPlaneSweep1.NoFlaps" })
        assertNull(missing.dragReferences()[1].dragArea)
        assertEquals(1.0, missing.dragReferences()[0].dragArea!!, 1e-10)
        assertNull(original.copy(basicMassKg = null).dragReferences()[0].areaPerTonne)
        assertNull(original.copy(maximumFuelMassKg = null).dragReferences()[0].massTimesInducedFactor)
        val noBody = original.copy(aerodynamicParts = original.aerodynamicParts.filterNot { it.kind == AerodynamicPartKind.FUSELAGE })
        assertTrue(noBody.dragReferences().all { it.dragArea == null })
    }
    @Test fun zerosInvalidInputsAndOverflowStayExplicit() {
        val original = model()
        val zero = original.copy(aerodynamicParts = original.aerodynamicParts.map { it.copy(cdMin = 0.0) })
        assertTrue(zero.dragReferences().all { it.dragArea == 0.0 && it.areaPerTonne == 0.0 })
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            val result = original.copy(aerodynamicParts = original.aerodynamicParts.map { it.copy(cdMin = bad) })
            assertTrue(result.dragReferences().all { it.dragArea == null })
        }
        assertNull(original.copy(basicMassKg = Double.MAX_VALUE, maximumFuelMassKg = Double.MAX_VALUE).dragReferences()[0].halfFuelMassKg)
        assertNull(original.copy(aerodynamicGeometry = original.aerodynamicGeometry.map { it.copy(efficiency = 0.0) }).dragReferences()[0].inducedFactor)
    }
    @Test fun fixedWingSiblingFallbackRequiresAbsentRatherThanRejectedLocalSource() {
        fun fixed(local: String) = FlightModelExtractor.extract(BlkParser.parse("""
            NoFlaps { CdMin:r=0.1 }
            Areas { Fuselage:r=2 }; Fuselage { CdMin:r=0.05 }
            WingPlane { Span:r=6; Areas { $areas }; $local }
        """)).dragReferences().single()
        assertEquals(1.0, fixed("").dragArea!!, 1e-10)
        assertEquals("NoFlaps", fixed("").cleanSource)
        assertNull(fixed("NoFlaps { CdMin:r=0.2 } NoFlaps { CdMin:r=0.3 }").cleanSource)
        assertNull(fixed("NoFlaps { CdMin:t=bad }").dragArea)
        assertNull(fixed("NoFlaps { CdMin:r=0.2 } NoFlaps { CdMin:r=0.3 } FlapsPolar0 { CdMin:r=0.1 }").cleanSource)
    }
    @Test fun radiatorCoefficientsKeepSourceAndSignAndRejectDuplicates() {
        val model = FlightModelExtractor.extract(BlkParser.parse("""
            RadiatorCd:r=0; OilRadiatorCd:r=-0.1
            Engine0 { RadiatorCd:r=0.2 }
            Engine1 { RadiatorCd:r=0.3; RadiatorCd:r=0.4; OilRadiatorCd:t=bad }
        """))
        assertEquals(5, model.radiatorDrag.size)
        assertEquals(0.0, model.radiatorDrag.first { it.sourcePath == "RadiatorCd" }.coefficient)
        assertEquals(-.1, model.radiatorDrag.first { it.sourcePath == "OilRadiatorCd" }.coefficient)
        assertEquals(.2, model.radiatorDrag.first { it.sourcePath == "Engine0.RadiatorCd" }.coefficient)
        assertNull(model.radiatorDrag.first { it.sourcePath == "Engine1.RadiatorCd" }.coefficient)
        assertTrue(model.issues.any { "Engine1.OilRadiatorCd" in it })
    }
}
