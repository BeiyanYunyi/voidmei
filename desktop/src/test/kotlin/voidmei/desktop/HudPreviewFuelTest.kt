package voidmei.desktop

import org.junit.Test
import kotlin.test.*
import voidmei.telemetry.HudField

class HudPreviewFuelTest {
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
