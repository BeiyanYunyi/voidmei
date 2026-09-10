package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class RadioAltitudeEstimateTest {
    private val base = TelemetryParser.parse("""{"valid":true,"H, m":1200}""", """{"valid":true,"radio_altitude":1000}""")!!
    private fun flight(unit: CockpitAltitudeUnit?, raw: Double? = 1000.0) =
        ConnectionState.Flying(base.copy(radioAltitudeRaw = raw), FlightMetrics(cockpitAltitudeUnit = unit))

    @Test fun radarConversionRequiresKnownInstrumentScaleAndValidRawValue() {
        val field = HudField.RADIO_ALTITUDE_ESTIMATE
        assertNull(field.value(flight(null)))
        assertEquals(1000.0, field.value(flight(CockpitAltitudeUnit.METRES)))
        assertEquals(304.8, field.value(flight(CockpitAltitudeUnit.FEET))!!, 1e-9)
        assertEquals(0.0, field.value(flight(CockpitAltitudeUnit.FEET, 0.0)))
        for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            for (unit in CockpitAltitudeUnit.entries) assertNull(field.value(flight(unit, invalid)))
        assertEquals(1000.0, HudField.RADIO_ALTITUDE_RAW.value(flight(CockpitAltitudeUnit.FEET)))
        assertEquals("m · 待判定", field.unitFor(base, FlightMetrics()))
        assertEquals("m · 按高度表单位推断", field.unitFor(base, FlightMetrics(cockpitAltitudeUnit = CockpitAltitudeUnit.FEET)))
    }

    @Test fun liveCalculatorConfirmsScaleAndClearsItAtFlightBoundaries() {
        val calculator = FlightCalculator()
        fun sample(i: Int, aircraft: String = "first") = base.copy(aircraft = aircraft,
            altitudeM = 1000.0 + i, altimeterRaw = 4000.0 + i * 3.28084)
        fun reading(t: Telemetry, time: Long): Double? = HudField.RADIO_ALTITUDE_ESTIMATE.value(
            ConnectionState.Flying(t, calculator.update(t, time)))
        for (i in 0..9) assertNull(reading(sample(i), i * 1000L))
        assertEquals(304.8, reading(sample(10), 10000)!!, 1e-9)
        assertNull(reading(sample(11, "second"), 11000))
        for (i in 12..20) assertNull(reading(sample(i, "second"), i * 1000L))
        assertEquals(304.8, reading(sample(21, "second"), 21000)!!, 1e-9)
        assertNull(reading(sample(24, "second"), 24000))
        for (i in 25..33) assertNull(reading(sample(i, "second"), i * 1000L))
        assertEquals(304.8, reading(sample(34, "second"), 34000)!!, 1e-9)
        calculator.reset()
        assertNull(reading(sample(35, "second"), 35000))
    }

    @Test fun legacyConvertedAltitudeSelectsEstimateAndKeepsRawField() {
        val imported = LegacySettingsReader.read("""(panel p
            (item radar :type data :target getRadioAltitude :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("future", "radio_altitude_raw")))
        assertEquals(listOf("future", "radio_altitude_raw", "radio_altitude_estimate"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
