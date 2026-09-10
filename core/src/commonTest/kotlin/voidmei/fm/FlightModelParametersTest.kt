package voidmei.fm

import kotlin.test.*

class FlightModelParametersTest {
    private fun model(text: String) = FlightModelExtractor.extract(BlkParser.parse(text))
    @Test fun gearLimitRequiresAnUnambiguousPositiveModelValue() {
        assertEquals(450.0, model("GearDestructionIndSpeed:r=450").gearLimitKmh)
        assertNull(model("EmptyMass:r=2500").gearLimitKmh)
        for (text in listOf("GearDestructionIndSpeed:r=0", "GearDestructionIndSpeed:r=-1",
            "A { GearDestructionIndSpeed:r=450 } B { GearDestructionIndSpeed:r=500 }")) {
            val result = model(text)
            assertNull(result.gearLimitKmh)
            assertTrue(result.issues.isNotEmpty())
        }
    }

    @Test fun legacyMassAndLimitsUseExactFields() {
        val result = model("""
            Mass { EmptyMass:r=2500; MaxFuelMass0:r=500 }
            Vne:r=800
            VneMach:r=0.85
            NoFlaps { alphaCritLow:r=-12; alphaCritHigh:r=18 }
            FullFlaps { alphaCritLow:r=-10; alphaCritHigh:r=14 }
        """.trimIndent())
        assertEquals(2500.0, result.emptyMassKg)
        assertEquals(500.0, result.maximumFuelMassKg)
        val limits = result.limits(null, 50.0)!!
        assertEquals(800.0, limits.vneKmh)
        assertEquals(0.85, limits.maxMach)
        assertEquals(-11.0, limits.minAngleOfAttackDeg)
        assertEquals(16.0, limits.maxAngleOfAttackDeg)
        assertTrue(result.issues.isEmpty())
    }

    private fun sweep(index: Int, ratio: Double, speed: Int, aoa: Int) = """
        WingPlaneSweep$index {
            Sweep:r=$ratio
            Strength { VNE:r=$speed; MNE:r=2 }
            FlapsPolar0 { alphaCritHigh:r=$aoa; alphaCritLow:r=-10 }
            FlapsPolar1 { alphaCritHigh:r=${aoa - 4}; alphaCritLow:r=-6 }
        }
    """.trimIndent()

    @Test fun arbitrarySweepNodesInterpolateBothFlapsAndSweep() {
        val result = model(sweep(7, 1.0, 1400, 26) + "\n" + sweep(0, 0.0, 800, 18) + "\n" + sweep(3, 0.25, 1000, 22))
        val limits = result.limits(0.125, 50.0)!!
        assertEquals(900.0, limits.vneKmh)
        assertEquals(18.0, limits.maxAngleOfAttackDeg)
        assertEquals(-8.0, limits.minAngleOfAttackDeg)
        assertNull(result.limits(null, 0.0))
        assertNull(result.limits(Double.NaN, 0.0))
        assertNull(result.limits(2.0, 0.0))
    }

    @Test fun duplicateOrMissingSweepInvalidatesInterpolation() {
        val duplicate = model(sweep(0, 0.0, 800, 18) + "\n" + sweep(1, 0.0, 1000, 22))
        assertNull(duplicate.limits(0.0, 0.0))
        assertTrue(duplicate.issues.isNotEmpty())
        val missing = model(sweep(0, 0.0, 800, 18) + "\nWingPlaneSweep1 { Strength { VNE:r=1000 } }")
        assertNull(missing.limits(0.0, 0.0))
    }

    @Test fun missingFlapDataIsNotZeroAndDoesNotPolluteCleanLimit() {
        val result = model("WingPlane { Strength { VNE:r=800 } NoFlaps { alphaCritHigh:r=18 } }")
        assertEquals(18.0, result.limits(null, 0.0)!!.maxAngleOfAttackDeg)
        assertNull(result.limits(null, 50.0)!!.maxAngleOfAttackDeg)
        assertNull(result.limits(null, null)!!.maxAngleOfAttackDeg)
        assertEquals(800.0, result.limits(null, null)!!.vneKmh)
    }

    @Test fun ambiguousSuffixesAndInvalidNumericTypesAreRejected() {
        val result = model("A { EmptyMass:r=10 } B { EmptyMass:r=20 } Vne:t=\"900\"")
        assertNull(result.emptyMassKg)
        assertNull(result.limits(null, 0.0)!!.vneKmh)
        assertEquals(2, result.issues.size)
    }

    @Test fun exactSweepBoundaryDoesNotRequireNeighbourData() {
        val result = model(sweep(0, 0.0, 800, 18) + "\nWingPlaneSweep1 { Sweep:r=0.5; Strength { VNE:r=1000 } }\n" + sweep(2, 1.0, 1200, 24))
        assertEquals(800.0, result.limits(0.0, 0.0)!!.vneKmh)
        assertEquals(1000.0, result.limits(0.5, 0.0)!!.vneKmh)
        assertNull(result.limits(0.25, 0.0)!!.maxAngleOfAttackDeg)
        assertEquals(24.0, result.limits(1.0, 0.0)!!.maxAngleOfAttackDeg)
    }

    @Test fun singleWingConfigurationDoesNotRequireSweepTelemetry() {
        val result = model("WingPlaneSweep0 { Strength { VNE:r=850 } NoFlaps { alphaCritHigh:r=19 } }")
        assertFalse(result.variableSweep)
        assertEquals(850.0, result.limits(null, 0.0)!!.vneKmh)
        assertEquals(19.0, result.limits(null, 0.0)!!.maxAngleOfAttackDeg)
    }
}
