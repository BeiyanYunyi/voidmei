package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class HudFuelRateTest {
    @Test fun fuelRateSelectionPersistsAndInvalidEstimatesRemainUnknown() {
        val field = HudField.FUEL_LOSS_RATE
        val settings = AppSettings(hudFields = listOf(field.id, "ias"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(field, HudField.IAS), HudField.selected(settings.hudFields))
        assertFalse(field.id in HudField.defaults)
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
        fun reading(value: Double?) = field.value(ConnectionState.Flying(telemetry, FlightMetrics(fuelConsumptionKgPerMinute = value)))
        assertEquals(0.0, reading(0.0))
        assertEquals(60.0, reading(60.0))
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(reading(value))
    }
}
