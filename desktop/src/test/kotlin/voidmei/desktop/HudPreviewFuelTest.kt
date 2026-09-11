package voidmei.desktop

import org.junit.Test
import kotlin.test.*
import voidmei.telemetry.HudField

class HudPreviewFuelTest {
    @Test fun selectedEngineSamplesContributeToTotalsAndMissingModeKeepsThemUnknown() {
        val flight = hudPreviewFlight(engineIndices = listOf(4, 4, 10))
        assertEquals(listOf(1, 2, 4, 10), flight.telemetry.engines.map { it.index })
        assertEquals(3550.0, flight.metrics.totalPowerHp)
        assertEquals(2750.0, flight.metrics.totalThrustKgf)
        val missing = hudPreviewFlight(missing = true, engineIndices = listOf(4, 10))
        assertEquals(listOf(1, 2, 4, 10), missing.telemetry.engines.map { it.index })
        assertTrue(missing.telemetry.engines.all { it.powerHp == null && it.waterTemperatureC == null })
    }
    @Test fun missingPreviewDoesNotInventFuelOrEngineValues() {
        val flight = hudPreviewFlight(missing = true)
        for (field in HudField.entries) assertNull(field.value(flight), field.id)
        assertEquals(listOf(1, 2), flight.telemetry.engines.map { it.index })
        flight.telemetry.engines.forEach { engine ->
            voidmei.telemetry.HudEngineField.entries.forEach { assertNull(it.value(engine), it.id) }
        }
    }
    @Test fun previewUsesSampledFuelEstimatesInBothModes() {
        for (warning in listOf(false, true)) {
            val flight = hudPreviewFlight(warning)
            assertEquals(60.0, HudField.FUEL_LOSS_RATE.value(flight))
            assertEquals(if (warning) 30.0 else 300.0, flight.metrics.fuelEnduranceSeconds)
            assertEquals(if (warning) 5.0 else 50.0, flight.metrics.fuelPercent)
            assertNotNull(flight.metrics.specificExcessPowerMps)
        }
    }
}
