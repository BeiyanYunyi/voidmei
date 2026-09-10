package voidmei.config

import kotlin.test.*

class AttitudeLimitSettingsTest {
    @Test fun visibilityDefaultsToTrueAndPersistsBothChoices() {
        assertTrue(SettingsJson.decode("""{"version":1}""").hudAttitudeAoaLimits)
        for (show in listOf(false, true)) {
            val settings = AppSettings(hudAttitudeAoaLimits = show)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertFails { SettingsJson.decode("""{"hudAttitudeAoaLimits":"false"}""") }
    }
    @Test fun legacyLimitSwitchDoesNotChangeOtherAttitudeOrWarningChoices() {
        for (show in listOf(false, true)) {
            val imported = LegacySettingsReader.read("""(panel p
                (item limit :type switch :target attitudeIndicatorDisplayAoALimits :value $show))""")
            val current = AppSettings(hudAttitude = false, hudAttitudeEarthFixed = true, hudAoaWarningPercent = 12.0)
            assertEquals(current.copy(hudAttitudeAoaLimits = show), imported.applyTo(current))
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
        }
    }
}
