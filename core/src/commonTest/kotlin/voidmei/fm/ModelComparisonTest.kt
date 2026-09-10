package voidmei.fm

import kotlin.test.*

class ModelComparisonTest {
    @Test fun fuelUsesEachCapacityAndMissingCapacityDoesNotBecomeZero() {
        val a = FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList(),
            stallSpeed = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 20.0, 40.0))),
            structuralLoad = StructuralLoadModel(-30000.0, 60000.0, 1000.0))
        val b = a.copy(maximumFuelMassKg = 300.0)
        val rows = ModelComparison.compare(a, b, fuelFraction = 0.5).associateBy { it.label }
        assertEquals(50.0, rows.getValue("比较燃油量").baseline)
        assertEquals(150.0, rows.getValue("比较燃油量").current)
        assertTrue(rows.getValue("1 G 失速 IAS 估算").delta!! > 0)
        assertTrue(rows.getValue("正过载限制估算").delta!! < 0)
        val flaps = ModelComparison.compare(a, b, flaps = 100.0, fuelFraction = 0.5).associateBy { it.label }
        assertEquals(rows.getValue("1 G 失速 IAS 估算").baseline!! / kotlin.math.sqrt(2.0),
            flaps.getValue("1 G 失速 IAS 估算").baseline!!, 1e-8)
        val missing = b.copy(maximumFuelMassKg = null)
        assertNull(ModelComparison.compare(a, missing, fuelFraction = 0.5).first { it.label == "1 G 失速 IAS 估算" }.current)
        assertNotNull(ModelComparison.compare(a, missing, fuelFraction = 0.0).first { it.label == "1 G 失速 IAS 估算" }.current)
        assertFails { ModelComparison.compare(a, b, fuelFraction = Double.NaN) }
        assertFails { ModelComparison.compare(a, b, fuelFraction = 1.1) }
    }
    @Test fun comparesBothModelsUnderTheSameFlapConditionAndPreservesZero() {
        val a = FlightModelParameters(0.0, null,
            listOf(WingConfiguration(0.0, 700.0, 0.8, -10.0, 20.0, -5.0, 30.0)), false, emptyList())
        val b = a.copy(emptyMassKg = 100.0, maximumFuelMassKg = 50.0,
            wings = listOf(WingConfiguration(0.0, 800.0, 0.9, -8.0, 24.0, -4.0, 36.0)))
        val rows = ModelComparison.compare(a, b, flaps = 50.0).associateBy { it.label }
        assertEquals(0.0, rows.getValue("空重").baseline)
        assertEquals(100.0, rows.getValue("空重").delta)
        assertNull(rows.getValue("燃油容量").delta)
        assertEquals(25.0, rows.getValue("迎角上限").baseline)
        assertEquals(30.0, rows.getValue("迎角上限").current)
        assertEquals(5.0, rows.getValue("迎角上限").delta)
        assertFails { ModelComparison.compare(a, b, sweep = Double.NaN) }
        assertFails { ModelComparison.compare(a, b, flaps = 101.0) }
        assertNull(ModelDifference("overflow", "", -Double.MAX_VALUE, Double.MAX_VALUE).delta)
    }
}
