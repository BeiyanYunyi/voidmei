package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class PropulsiveEfficiencyTest {
    private val base = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,
        "power 1, hp":500,"power 2, hp":1000,"thrust 1, kgs":200,"thrust 2, kgs":500}""",
        """{"valid":true}""")!!
    private fun value(t: Telemetry): Double? = HudField.PROPULSIVE_EFFICIENCY.value(
        ConnectionState.Flying(t, FlightCalculator().update(t, 0)))

    @Test fun usesCombinedPowerAndThrustWithoutTruncatingOrAveragingIndividualEfficiencies() {
        assertEquals(62.2644444444, value(base)!!, 1e-8)
        assertEquals(value(base), value(base.copy(engines = base.engines.reversed())))
        assertEquals(0.0, value(base.copy(tasKmh = 0.0)))
        assertEquals(0.0, value(base.copy(engines = base.engines.map { it.copy(thrustKgf = 0.0) })))
        assertNull(value(base.copy(tasKmh = null)))
        assertNull(value(base.copy(engines = emptyList())))
        assertNull(value(base.copy(engines = base.engines.map { it.copy(powerHp = 0.0) })))
        assertNull(value(base.copy(engines = base.engines.map { if (it.index == 2) it.copy(powerHp = null) else it })))
        assertNull(value(base.copy(engines = base.engines.map { if (it.index == 2) it.copy(thrustKgf = null) else it })))
    }

    @Test fun legacySwitchImportsAndPersistsAlongsideOtherFields() {
        val imported = LegacySettingsReader.read("""(panel p
            (item efficiency :type data :target getPropEfficiency :value true))""")
        val result = imported.applyTo(AppSettings(hudFields = listOf("future", "ias")))
        assertEquals(listOf("future", "ias", "propulsive_efficiency"), result.hudFields)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
    }
}
