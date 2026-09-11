package voidmei.telemetry

import kotlin.test.*

class HudEngineValidityTest {
    @Test fun compressorStagesArePositiveIntegersWithoutChangingRawTelemetry() {
        val base = TelemetryParser.parse("""{"valid":true,"compressor stage 1":0}""",
            """{"valid":true,"type":"test"}""")!!.engines.single()
        assertEquals(0.0, base.compressorStage)
        for (value in listOf(null, -1.0, 0.0, 0.5, 1.5, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(HudEngineField.COMPRESSOR.value(base.copy(compressorStage = value)), "Stage $value")
        for (value in listOf(1.0, 2.0, 3.0))
            assertEquals(value, HudEngineField.COMPRESSOR.value(base.copy(compressorStage = value)))
    }

    @Test fun unavailableControlsAreUnknownButZeroAndRichMixtureRemainValid() {
        val base = TelemetryParser.parse(
            """{"valid":true,"RPM throttle 1, %":-1,"mixture 1, %":-1}""",
            """{"valid":true,"type":"test"}""")!!.engines.single()
        assertEquals(-1.0, base.rpmControlPercent)
        assertEquals(-1.0, base.mixturePercent)
        for (value in listOf(null, -1.0, -0.5, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 100.0, 120.0)) {
            val engine = base.copy(rpmControlPercent = value, mixturePercent = value,
                radiatorPercent = value, oilRadiatorPercent = value)
            val expected = value?.takeIf { it.isFinite() && it >= 0 }
            assertEquals(expected, HudEngineField.RPM_CONTROL.value(engine), "RPM control $value")
            assertEquals(expected, HudEngineField.MIXTURE.value(engine), "Mixture $value")
            assertEquals(expected, HudEngineField.RADIATOR.value(engine), "Radiator $value")
            assertEquals(expected, HudEngineField.OIL_RADIATOR.value(engine), "Oil radiator $value")
        }
    }

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
