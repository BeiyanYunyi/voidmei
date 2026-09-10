package voidmei.fm

import kotlin.test.*
import voidmei.telemetry.*

class SweptStructuralLoadTest {
    private val source = """
        Mass { EmptyMass:r=900; OilMass:r=75; MaxNitro:r=25; MaxFuelMass0:r=1000 }
        Aerodynamics {
            WingPlaneSweep0 { Sweep:r=0; Strength { CritOverload:p2=-49000,98000 } }
            WingPlaneSweep1 { Sweep:r=1; Strength { CritOverload:p2=-49000,147000 } }
        }
    """
    private fun extract(text: String = source) = FlightModelExtractor.extract(BlkParser.parse(text))

    @Test fun interpolatesSweepProfilesAndUsesCurrentFuel() {
        val parameters = extract()
        assertNull(parameters.structuralLoad)
        assertNotNull(parameters.sweptStructuralLoad)
        for ((sweep, expected) in listOf(0.0 to 10.8, 0.5 to 13.8, 1.0 to 16.8)) {
            val limits = assertNotNull(parameters.loadLimits(1000.0, sweep))
            assertEquals(-4.8, limits.minimumG, 1e-10)
            assertEquals(expected, limits.maximumG, 1e-10)
        }
        assertEquals(28.8, assertNotNull(parameters.loadLimits(0.0, 0.5)).maximumG, 1e-10)
        for (sweep in listOf(null, -0.1, 1.1, Double.NaN)) assertNull(parameters.loadLimits(1000.0, sweep))
        for (fuel in listOf(null, -1.0, Double.NaN, Double.MAX_VALUE)) assertNull(parameters.loadLimits(fuel, 0.5))
        val inset = extract(source.replace("Sweep:r=0;", "Sweep:r=0.2;").replace("Sweep:r=1;", "Sweep:r=0.8;"))
        assertEquals(parameters.loadLimits(1000.0, 0.0), inset.loadLimits(1000.0, 0.0))
        assertEquals(parameters.loadLimits(1000.0, 1.0), inset.loadLimits(1000.0, 1.0))
    }

    @Test fun partialAndAmbiguousTablesNeverFallBackToOneKnownProfile() {
        for (bad in listOf(
            source.replace("CritOverload:p2=-49000,147000", ""),
            source.replace("CritOverload:p2=-49000,147000", "CritOverload:r=147000"),
            source.replace("CritOverload:p2=-49000,147000", "CritOverload:p2=49000,147000"),
            source.replace("Sweep:r=1;", ""), source.replace("Sweep:r=1;", "Sweep:r=0;"),
            source.replace("Sweep:r=1;", "Sweep:r=2;"), source.replace("Sweep:r=1;", "Sweep:r=1; Sweep:r=1;"),
            source.replace("OilMass:r=75;", ""),
        )) {
            val parameters = extract(bad + "\nWingCritOverload:p2=-49000,98000")
            assertNull(parameters.sweptStructuralLoad, bad)
            assertNull(parameters.loadLimits(0.0, 0.5), bad)
            assertTrue(parameters.issues.any { it.startsWith("Structural load sweep table:") }, bad)
        }
        val single = extract("""Mass { EmptyMass:r=900; OilMass:r=75; MaxNitro:r=25 }
            WingPlaneSweep0 { Strength { CritOverload:p2=-49000,98000 } }""")
        assertEquals(10.8, assertNotNull(single.loadLimits(1000.0, null)).maximumG, 1e-10)
    }

    @Test fun warningsAndComparisonQueryTheSameSweepAndAircraft() {
        val parameters = extract()
        val model = AircraftAlertModel("test", parameters)
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", fuelKg = 1000.0, loadG = 12.0)
        fun warns(sweep: Double?, aircraft: String? = "test", fuel: Double? = 1000.0): Boolean {
            val flight = ConnectionState.Flying(base.copy(wingSweepRatio = sweep, aircraft = aircraft, fuelKg = fuel), FlightMetrics())
            return FlightAlert.LOAD_LIMIT in FlightAlerts().updateForAircraft(flight, model, 0, true).active
        }
        assertTrue(warns(0.0))
        assertFalse(warns(1.0))
        assertFalse(warns(null))
        assertFalse(warns(0.0, "other"))
        assertFalse(warns(0.0, fuel = null))
        val row = ModelComparison.compare(parameters, parameters, sweep = 0.5, fuelFraction = 1.0)
            .single { it.label == "正过载限制估算" }
        assertEquals(13.8, assertNotNull(row.baseline), 1e-10)
        assertEquals(row.baseline, row.current)
    }
}
