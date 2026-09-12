package voidmei.fm

import kotlin.test.*

class AerodynamicPartsTest {
    private fun extract(s: String) = AerodynamicPartsExtractor.extract(BlkParser.parse(s))
    @Test fun keepsAliasesAndSweepSourcesSeparateWithoutInventingMissingValues() {
        val result = extract("""
            NoFlaps { CdMin:r=0; Cl0:r=-0.1 }
            FlapsPolar0 { alphaCritHigh:r=15 }
            WingPlaneSweep0 { Sweep:r=0; NoFlaps { CdMin:r=0.01; alphaCritHigh:r=16 } }
            WingPlaneSweep2 { Sweep:r=1; FullFlaps { ClCritLow:r=-1.1; ClCritHigh:r=1.8 } }
            FuselagePlane { Polar { CdMin:r=0.05 } }
            HorStabPlane { Polar { Cl0:r=0.2 } }
            VerStabPlane { Polar { alphaCritLow:r=-20 } }
        """)
        assertTrue(result.issues.isEmpty())
        assertEquals(7, result.parts.size)
        val old = result.parts.first { it.sourcePath == "NoFlaps" }
        assertEquals(0.0, old.cdMin); assertEquals(-.1, old.cl0); assertNull(old.alphaHigh)
        assertEquals(0.0, result.parts.first { it.sourcePath == "WingPlaneSweep0.NoFlaps" }.sweepRatio)
        assertEquals(1.0, result.parts.first { it.sourcePath == "WingPlaneSweep2.FullFlaps" }.sweepRatio)
        assertEquals(AerodynamicPartKind.FIN, result.parts.first { it.sourcePath == "HorStabPlane.Polar" }.kind)
        assertEquals(AerodynamicPartKind.STAB, result.parts.first { it.sourcePath == "VerStabPlane.Polar" }.kind)
        assertEquals(AerodynamicPartKind.FUSELAGE, result.parts.first { it.sourcePath == "FuselagePlane.Polar" }.kind)
    }
    @Test fun duplicateSourcesAreRejectedAndInvalidFieldsStayUnknownLocally() {
        val result = extract("""
            NoFlaps { CdMin:r=1 } NoFlaps { CdMin:r=2 }
            WingPlaneSweep0 { Sweep:r=2; FullFlaps { CdMin:r=NaN; Cl0:r=1; Cl0:r=2; alphaCritHigh:r=20 } }
        """)
        assertEquals(1, result.parts.size)
        val part = result.parts.single()
        assertNull(part.sweepRatio); assertNull(part.cdMin); assertNull(part.cl0); assertEquals(20.0, part.alphaHigh)
        assertEquals(4, result.issues.size)
        val model = FlightModelExtractor.extract(BlkParser.parse("Fin { Cl0:t=invalid }"))
        assertNull(model.aerodynamicParts.single().cl0)
        assertTrue(model.issues.any { "Fin.Cl0" in it })
        assertTrue(extract("Other { Polar { CdMin:r=0.1 } }").parts.isEmpty())
    }
}
