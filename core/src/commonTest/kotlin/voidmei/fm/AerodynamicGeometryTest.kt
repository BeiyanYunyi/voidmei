package voidmei.fm

import kotlin.test.*

class AerodynamicGeometryTest {
    private fun areas(prefix: String = "", value: String = "1") =
        listOf("LeftIn", "LeftMid", "LeftOut", "LeftCut", "RightIn", "RightMid", "RightOut", "RightCut", "Aileron")
            .joinToString("; ") { "${if (it == "Aileron") it else prefix + it}:r=$value" }

    @Test fun extractsLegacyAndModernGeometryWithoutMixingProfiles() {
        val result = AerodynamicGeometryExtractor.extract(BlkParser.parse("""
            Areas { ${areas("Wing")}; Fuselage:r=2 }
            Wingspan:r=6; SweptWingAngle:r=-5; OswaldsEfficiencyNumber:r=0.8
            WingPlaneSweep0 { Sweep:r=0; Span:r=9; Areas { ${areas()} }; OswaldsEfficiencyNumber:r=0.7 }
            WingPlaneSweep1 { Sweep:r=1; Span:r=3; Areas { ${areas(value = "2")} } }
        """))
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        assertEquals(3, result.wings.size)
        val (legacy, first, last) = result.wings
        assertEquals(9.0, legacy.wingArea); assertEquals(4.0, legacy.aspectRatio)
        assertEquals(-5.0, legacy.sweptAngle)
        assertEquals(9.0, first.aspectRatio); assertEquals(.7, first.efficiency)
        assertEquals("WingPlaneSweep0.OswaldsEfficiencyNumber", first.efficiencySource)
        assertEquals(18.0, last.wingArea); assertEquals(.5, last.aspectRatio)
        assertEquals(.8, last.efficiency); assertEquals("OswaldsEfficiencyNumber", last.efficiencySource)
        assertTrue(result.wings.all { it.bodyArea == 2.0 && it.bodySource == "Areas.Fuselage" })
    }

    @Test fun missingComponentsAndProfileLocalValuesNeverBecomeGlobalDefaults() {
        val model = FlightModelExtractor.extract(BlkParser.parse("""
            WingPlaneSweep0 { Sweep:r=0; OswaldsEfficiencyNumber:r=0.7; Areas { Fuselage:r=5; ${areas()} } }
            WingPlaneSweep1 { Sweep:r=1; Span:r=3; Areas { LeftIn:r=1 } }
        """))
        val last = model.aerodynamicGeometry.last()
        assertNull(last.wingArea); assertNull(last.aspectRatio)
        assertNull(last.efficiency); assertNull(last.bodyArea)
        assertTrue(model.issues.any { "面积分量不完整" in it })
        assertTrue(AerodynamicGeometryExtractor.extract(BlkParser.parse("Areas { Fuselage:r=5 }")).wings.isEmpty())
    }

    @Test fun duplicateInvalidAndZeroValuesAreHandledWithoutSilentFallback() {
        val result = AerodynamicGeometryExtractor.extract(BlkParser.parse("""
            OswaldsEfficiencyNumber:r=0.8
            WingPlane { Span:r=1 } WingPlane { Span:r=2 }
            WingPlaneSweep0 { Sweep:r=2; Span:r=NaN; SweptAngle:t=bad;
                OswaldsEfficiencyNumber:r=-1; Areas { ${areas(value = "0")} } }
        """))
        val wing = result.wings.single()
        assertEquals(0.0, wing.wingArea); assertNull(wing.aspectRatio)
        assertNull(wing.sweepRatio); assertNull(wing.span); assertNull(wing.sweptAngle); assertNull(wing.efficiency)
        assertEquals(5, result.issues.size)
        assertNull(wing.copy(wingArea = 1.0, span = Double.MAX_VALUE).aspectRatio)
    }

    @Test fun effectiveAreaUsesExplicitPositiveMassAndRejectsOverflow() {
        val profile = StallLiftProfile(0.0, 10.0, 20.0)
        assertEquals(5.0, profile.areaPerTonne(2000.0, false))
        assertEquals(10.0, profile.areaPerTonne(2000.0, true))
        for (mass in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE))
            assertNull(profile.areaPerTonne(mass, false))
        assertNull(profile.copy(cleanArea = Double.POSITIVE_INFINITY).areaPerTonne(1000.0, false))
    }
}
