package voidmei.fm

import kotlin.test.*
import voidmei.telemetry.*

class StructuralLoadModelTest {
    private val source = """
        Mass { EmptyMass:r=900; OilMass:r=75; MaxNitro:r=25 }
        WingCritOverload:p2=-49000,98000
    """
    private fun extract(text: String = source) = FlightModelExtractor.extract(BlkParser.parse(text))

    @Test fun legacyFormulaAccountsForCurrentFuelMass() {
        val model = assertNotNull(extract().structuralLoad)
        assertEquals(1000.0, model.basicMassKg)
        val full = assertNotNull(model.limits(1000.0))
        assertEquals(-4.8, full.minimumG, 1e-10)
        assertEquals(10.8, full.maximumG, 1e-10)
        val empty = assertNotNull(model.limits(0.0))
        assertEquals(-10.8, empty.minimumG, 1e-10)
        assertEquals(22.8, empty.maximumG, 1e-10)
        for (fuel in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
            assertNull(model.limits(fuel))
    }

    @Test fun requiresKnownMassesAndUnambiguousValidForcePair() {
        val alternate = source.replace("WingCritOverload:p2=-49000,98000", "WingPlane { Strength { CritOverload:p2=-49000,98000 } }")
        assertEquals(extract().structuralLoad, extract(alternate).structuralLoad)
        for (bad in listOf(
            source.replace("OilMass:r=75;", ""), source.replace("MaxNitro:r=25", "MaxNitro:r=-1"),
            source.replace("p2=-49000,98000", "p2=49000,98000"),
            source.replace("p2=-49000,98000", "r=98000"),
            source + "\nWingCritOverload:p2=-49000,98000",
            alternate + "\nOther { Strength { CritOverload:p2=-49000,98000 } }",
        )) {
            val result = extract(bad)
            assertNull(result.structuralLoad, bad)
            assertTrue(result.issues.isNotEmpty(), bad)
        }
        assertNotNull(extract(source.replace("OilMass:r=75", "OilMass:r=0").replace("MaxNitro:r=25", "MaxNitro:r=0")).structuralLoad)
    }

    @Test fun warningUsesStrictBoundariesCurrentFuelAndCurrentAircraft() {
        val model = AircraftAlertModel("test", extract())
        val limits = model.parameters.structuralLoad!!.limits(1000.0)!!
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", fuelKg = 1000.0)
        fun active(load: Double?, fuel: Double? = 1000.0, aircraft: String? = "test") = FlightAlerts().updateForAircraft(
            ConnectionState.Flying(base.copy(loadG = load, fuelKg = fuel, aircraft = aircraft), FlightMetrics()), model, 0, true)
        for (boundary in listOf(limits.minimumG, limits.maximumG, null, Double.NaN))
            assertFalse(FlightAlert.LOAD_LIMIT in active(boundary).active)
        assertEquals(FlightAlert.LOAD_LIMIT, active(limits.maximumG + .01).voice)
        assertEquals(FlightAlert.LOAD_LIMIT, active(limits.minimumG - .01).voice)
        assertFalse(FlightAlert.LOAD_LIMIT in active(11.0, fuel = 0.0).active)
        assertFalse(FlightAlert.LOAD_LIMIT in active(11.0, fuel = null).active)
        assertFalse(FlightAlert.LOAD_LIMIT in active(11.0, aircraft = "other").active)
        assertFalse(FlightAlert.LOAD_LIMIT in active(11.0, aircraft = null).active)
        assertEquals(FlightAlert.LOAD_LIMIT, active(11.0, aircraft = "TEST").voice)
    }
}
