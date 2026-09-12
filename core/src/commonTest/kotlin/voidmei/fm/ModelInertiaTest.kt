package voidmei.fm

import kotlin.test.*

class ModelInertiaTest {
    private fun extract(s: String) = FlightModelExtractor.extract(BlkParser.parse(s))
    @Test fun axisMappingAndSourcePrecedencePreserveRawValues() {
        assertEquals(ModelInertia(300.75, 100.25, 200.5, "Mass.MomentOfInertia"),
            extract("Mass { MomentOfInertia:p3=100.25,200.5,300.75 }").inertia)
        assertEquals(ModelInertia(2.0, 0.0, 1.0, "MomentOfInertia"),
            extract("MomentOfInertia:p3=0,1,2\nMass { MomentOfInertia:p3=100,200,300 }").inertia)
        assertNull(extract("Mass { EmptyMass:r=2500 }").inertia)
    }
    @Test fun invalidAndAmbiguousVectorsReportIssuesWithoutFabricatingAxes() {
        for (source in listOf("MomentOfInertia:p2=1,2", "MomentOfInertia:r=1", "MomentOfInertia:p3=1,2",
            "MomentOfInertia:p3=1,2,NaN", "MomentOfInertia:p3=1,-2,3", "MomentOfInertia:p3=1,2,Infinity",
            "MomentOfInertia:p3=1,2,3\nMomentOfInertia:p3=4,5,6",
            "A { MomentOfInertia:p3=1,2,3 } B { MomentOfInertia:p3=4,5,6 }")) {
            val model = extract(source)
            assertNull(model.inertia)
            assertTrue(model.issues.any { "MomentOfInertia" in it })
        }
    }
}
