package voidmei.telemetry

import kotlin.test.*

class HudEngineValidityTest {
    @Test fun throttleAndRpmMatchFlightReadingsWithoutRejectingSignedChannels() {
        val base = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110,"RPM 1":2200,"water temp 1, C":-20,"magneto 1":-1}""",
            """{"valid":true,"type":"test"}""")!!
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.0, 110.0, 2200.0)) {
            val engine = base.engines.single().copy(throttlePercent = value, rpm = value)
            val flight = ConnectionState.Flying(base.copy(engines = listOf(engine)), FlightMetrics())
            assertEquals(HudField.ENGINE1_THROTTLE.value(flight), HudEngineField.THROTTLE.value(engine))
            assertEquals(HudField.ENGINE1_RPM.value(flight), HudEngineField.RPM.value(engine))
            assertEquals(-20.0, HudEngineField.WATER_TEMPERATURE.value(engine))
            assertEquals(-1.0, HudEngineField.MAGNETO.value(engine))
        }
    }
}
