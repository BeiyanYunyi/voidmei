package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class BoosterFuelTest {
    private fun read(fields: String) = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":500,$fields}""", """{"valid":true}""")!!
    private fun percent(t: Telemetry) = HudField.BOOSTER_FUEL_PERCENT.value(ConnectionState.Flying(t, FlightMetrics()))

    @Test fun exactChannelKeysKeepMainFuelSeparateAndPreserveEmptyTank() {
        for (suffix in listOf("", ", kg")) {
            val t = read(""""Mfuel 1$suffix":50,"Mfuel0 1$suffix":200,"Mfuel 10$suffix":999""")
            assertEquals(500.0, t.fuelKg)
            assertEquals(50.0, t.boosterFuelKg)
            assertEquals(25.0, percent(t))
            assertEquals(0.0, percent(t.copy(boosterFuelKg = 0.0)))
            assertEquals(100.0, percent(t.copy(boosterFuelKg = 300.0)))
            assertNull(percent(t.copy(boosterFuelCapacityKg = 0.0)))
        }
        assertNull(read(""""Mfuel 10, kg":999""").boosterFuelKg)
        for (value in listOf("null", "-65535", "-1", "\"50\"")) {
            assertNull(read(""""Mfuel 1, kg":$value""").boosterFuelKg)
        }
        assertNull(read(""""Mfuel 1":50,"Mfuel 1, kg":70""").boosterFuelKg)
    }

    @Test fun bothLegacySwitchesImportAndPersist() {
        val imported = LegacySettingsReader.read("""(panel p
            (item mass :type data :target getBoosterFuelKg :value true)
            (item ratio :type data :target getBoosterFuelPercent :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("fuel")))
        assertEquals(listOf("fuel", "booster_fuel", "booster_fuel_percent"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
