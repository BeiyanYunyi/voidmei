package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class FuelTimeFormatTest {
    @Test fun upperBoundsNeverDisplayPrematureExhaustion() {
        for ((seconds, shown) in listOf(0.0 to "00:00", 0.001 to "00:01", 0.8 to "00:01",
            18.5 to "00:19", 59.99 to "01:00", 60.0 to "01:00", 3600.1 to "60:01")) {
            assertEquals(shown, formatFuelTimeUpperBound(seconds))
        }
        assertEquals("00:18", formatFuelTime(18.5))
        assertEquals(0.1, roundUpperBound(0.01, 1))
        assertEquals(9.3, roundUpperBound(9.24, 1))
        assertEquals(0.0, roundUpperBound(0.0, 1))
        assertEquals(Double.MAX_VALUE, roundUpperBound(Double.MAX_VALUE, 1))
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals("—", formatFuelTimeUpperBound(value))
            assertNull(roundUpperBound(value, 1))
        }
        assertEquals("—", formatFuelTimeUpperBound(Long.MAX_VALUE.toDouble()))
    }

    @Test fun formatsWholeSecondsWithoutWrappingOrClampingMinutes() {
        for ((seconds, text) in listOf(0.0 to "00:00", 9.9 to "00:09", 59.99 to "00:59",
            60.0 to "01:00", 3599.0 to "59:59", 3600.0 to "60:00", 60000.0 to "1000:00")) {
            assertEquals(text, formatFuelTime(seconds))
        }
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE.toDouble())) {
            assertEquals("—", formatFuelTime(value))
        }
    }

    @Test fun legacyTimeChoiceIsMergedAndRoundTripsWithoutChangingMinuteField() {
        fun imported(enabled: Boolean) = LegacySettingsReader.read("""(panel p
            (item time :type data :target "getFuelTimeMili * 0.001" :value $enabled))""")
        val original = AppSettings(hudFields = listOf("future", "endurance"))
        val updated = imported(true).applyTo(original)
        assertEquals(listOf("future", "endurance", "endurance_clock"), updated.hudFields)
        assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
        assertEquals(original, imported(false).applyTo(updated))
        assertFalse("endurance_clock" in HudField.defaults)
    }
}
