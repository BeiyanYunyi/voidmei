package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*
import voidmei.fm.*

class MassEstimateHudTest {
    private fun parameters(mass: String) = FlightModelExtractor.extract(BlkParser.parse("Mass { $mass }"))
    private val complete = "EmptyMass:r=3000; OilMass:r=25; MaxNitro:r=75"

    @Test fun basicMassDoesNotDependOnStructuralOrLiftData() {
        val fm = parameters(complete)
        assertEquals(3100.0, fm.basicMassKg)
        assertNull(fm.structuralLoad)
        assertNull(fm.stallSpeed)
        for (fields in listOf("EmptyMass:r=3000", "EmptyMass:r=3000; OilMass:r=25",
            "EmptyMass:r=3000; OilMass:r=-1; MaxNitro:r=0",
            "$complete; OilMass:r=25", "EmptyMass:r=1e308; OilMass:r=1e308; MaxNitro:r=0")) {
            assertNull(parameters(fields).basicMassKg, fields)
        }
        assertEquals(3000.0, parameters("EmptyMass:r=3000; OilMass:r=0; MaxNitro:r=0").basicMassKg)
    }

    @Test fun matchesAircraftAndRetainsUnknownFuelInsteadOfAssumingEmptyTanks() {
        val base = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":400}""", """{"valid":true,"type":"test-plane"}""")!!
        val model = AircraftAlertModel("test-plane", parameters(complete))
        fun value(t: Telemetry, fm: AircraftAlertModel? = model) = HudField.MASS_ESTIMATE.value(
            ConnectionState.Flying(t, FlightMetrics()), fm)
        assertEquals(3500.0, value(base))
        assertEquals(3100.0, value(base.copy(fuelKg = 0.0)))
        assertNull(value(base, null))
        assertNull(value(base.copy(aircraft = "other-plane")))
        for (fuel in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(value(base.copy(fuelKg = fuel)))
        assertNull(value(base.copy(fuelKg = Double.MAX_VALUE), model.copy(parameters = model.parameters.copy(basicMassKg = Double.MAX_VALUE))))
        val imported = LegacySettingsReader.read("""(panel p (item mass :type data :target getTotalWeight :value true))""")
        val updated = imported.applyTo(AppSettings(hudFields = listOf("future", "fuel")))
        assertEquals(listOf("future", "fuel", "mass_estimate"), updated.hudFields)
        assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
    }
}
