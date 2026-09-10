package voidmei.fm

import kotlin.test.*

class PistonPowerCorrectionsTest {
    @Test fun matchesLegacyReferenceValues() {
        // Evaluated with the unchanged src/prog/util/PistonPowerModel.java.
        assertEquals(1.0355113636363635, PistonPowerCorrections.rpmPowerMultiplier(2400.0, 2700.0)!!, 1e-12)
        assertEquals(1.2100000000000002,
            PistonPowerCorrections.superchargerRpmMultiplier(2400.0, 2700.0, 0.2, 1.0)!!, 1e-12)
        assertEquals(1.299960255681818,
            PistonPowerCorrections.wepPowerMultiplier(1.2, 1.05, 0.98, 1.1, 2400.0, 2700.0)!!, 1e-12)
        assertEquals(4521.6396350521145,
            PistonPowerCorrections.wepCriticalAltitude(5000.0, 1.3, 1.6, 1.1, 1.05)!!, 1e-8)
    }

    @Test fun neutralInputsPreservePowerAndCriticalAltitude() {
        assertEquals(1.0, PistonPowerCorrections.rpmPowerMultiplier(2500.0, 2500.0))
        assertEquals(1.0, PistonPowerCorrections.superchargerRpmMultiplier(2500.0, 2500.0, 0.2, 1.0))
        assertEquals(1.0, PistonPowerCorrections.wepPowerMultiplier(1.0, 1.0, 1.0, 1.0, 2500.0, 2500.0))
        for (altitude in listOf(-500.0, 0.0, 5000.0, 10000.0)) {
            assertEquals(altitude, PistonPowerCorrections.wepCriticalAltitude(altitude, 1.3, 1.3, 1.0, 1.0)!!, 1e-8)
        }
        // RPM units cancel, even when a direct cubic calculation would overflow.
        assertEquals(PistonPowerCorrections.rpmPowerMultiplier(2400.0, 2700.0)!!,
            PistonPowerCorrections.rpmPowerMultiplier(2.4e200, 2.7e200)!!, 1e-12)
    }

    @Test fun increasingRequiredManifoldPressureLowersCriticalAltitude() {
        val normal = PistonPowerCorrections.wepCriticalAltitude(5000.0, 1.3, 1.3, 1.0, 1.0)!!
        val boosted = PistonPowerCorrections.wepCriticalAltitude(5000.0, 1.3, 1.6, 1.0, 1.0)!!
        val fasterCompressor = PistonPowerCorrections.wepCriticalAltitude(5000.0, 1.3, 1.6, 1.1, 1.0)!!
        assertTrue(boosted < normal)
        assertTrue(fasterCompressor > boosted)
    }

    @Test fun undefinedCorrectionsDoNotBecomeNeutralBoosts() {
        for (rpm in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(PistonPowerCorrections.rpmPowerMultiplier(rpm, 2700.0))
            assertNull(PistonPowerCorrections.superchargerRpmMultiplier(2400.0, rpm, 0.2, 1.0))
            assertNull(PistonPowerCorrections.wepPowerMultiplier(1.2, 1.0, 1.0, 1.0, rpm, 2700.0))
        }
        assertNull(PistonPowerCorrections.rpmPowerMultiplier(4500.0, 3000.0))
        assertNull(PistonPowerCorrections.superchargerRpmMultiplier(2400.0, 4800.0, 3.0, 0.5))
        assertNull(PistonPowerCorrections.wepPowerMultiplier(1.2, 1.0, 1.0, -1.0, 2400.0, 2700.0))
        assertNull(PistonPowerCorrections.wepCriticalAltitude(5000.0, 0.0, 1.6, 1.1, 1.0))
        assertNull(PistonPowerCorrections.wepCriticalAltitude(Double.NaN, 1.3, 1.6, 1.1, 1.0))
    }
}
