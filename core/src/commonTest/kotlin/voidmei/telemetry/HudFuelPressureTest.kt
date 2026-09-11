package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class HudFuelPressureTest {
    @Test fun rawGaugeSelectionAndValidationPreserveSourceSemantics() {
        val field = HudField.FUEL_PRESSURE_RAW
        val settings = AppSettings(hudFields = listOf(field.id, "ias"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(field, HudField.IAS), HudField.selected(settings.hudFields))
        assertFalse(field.id in HudField.defaults)
        val telemetry = TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"fuel_pressure":9.71,"fuel_pressure1":123}""")!!
        fun reading(value: Double?) = field.value(ConnectionState.Flying(telemetry.copy(fuelPressureRaw = value), FlightMetrics()))
        assertEquals(9.71, field.value(ConnectionState.Flying(telemetry, FlightMetrics())))
        assertEquals("仪表单位", field.unitFor(telemetry))
        assertEquals(0.0, reading(0.0))
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(reading(value))
    }
}
