package voidmei.config

import kotlin.test.*
import voidmei.telemetry.AttitudeGeometry

class AttitudeNorthPointerTest {
    @Test fun headingUpNorthHandlesCardinalsWrappingAndMissingData() {
        for ((heading, expected) in listOf(0.0 to (0.0 to -1.0), 90.0 to (-1.0 to 0.0),
            180.0 to (0.0 to 1.0), 270.0 to (1.0 to 0.0), -90.0 to (1.0 to 0.0), 360.0 to (0.0 to -1.0))) {
            val actual = AttitudeGeometry.northDirection(heading)!!
            assertEquals(expected.first, actual.first, 1e-12)
            assertEquals(expected.second, actual.second, 1e-12)
        }
        for (heading in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(AttitudeGeometry.northDirection(heading))
    }

    @Test fun settingPersistsImportsAndResetsWithoutChangingOtherPreferences() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudAttitudeNorthPointer)
        assertFails { SettingsJson.decode("""{"hudAttitudeNorthPointer":"true"}""") }
        for (visible in listOf(false, true)) {
            val imported = LegacySettingsReader.read("""(panel p
                (item x :target attitudeIndicatorDisplayDirection :type switch :value $visible))""")
            val original = AppSettings(hudAttitude = false, hudCompassHeadingUp = true, hudEnabled = false)
            val updated = imported.applyTo(original)
            assertEquals(original.copy(hudAttitudeNorthPointer = visible), updated)
            assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertFalse(updated.withHudLayout(AppSettings()).hudAttitudeNorthPointer)
            assertEquals(visible, AppSettings().withHudLayout(updated).hudAttitudeNorthPointer)
        }
        assertFalse(LegacySettingsReader.read("""(panel p
            (item x :target attitudeIndicatorDisplayDirection :type switch-inv :value true))""").hudAttitudeNorthPointer!!)
        assertFails { LegacySettingsReader.read("""(panel p
            (item x :target attitudeIndicatorDisplayDirection :type switch :value nope))""") }
    }
}
