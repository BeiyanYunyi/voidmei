package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class CompressorAdviceTest {
    @Test fun instanceOverridesProduceIndependentCompressorModels() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("""
            EngineType0 {
                Main { Type:t=Inline; Power:r=800 }
                Propellor { ThrottleRPMAuto0:p2=1,3000 }
                Compressor { NumSteps:i=2; Altitude0:r=1000; Power0:r=1000; Altitude1:r=1000; Power1:r=1500 }
            }
            Engine0 { Type:i=0 }
            Engine1 { Type:i=0; Compressor { Power1:r=500 } }
        """))
        assertEquals(setOf(1, 2), parameters.engineCompressors.keys)
        assertEquals(1500.0, parameters.engineCompressors.getValue(1).military.stages[1].critPower)
        assertEquals(500.0, parameters.engineCompressors.getValue(2).military.stages[1].critPower)
        val both = telemetry.copy(engines = telemetry.engines + telemetry.engines.map { it.copy(index = 2) })
        assertEquals(listOf(CompressorRecommendation(1, 1, 2)), CompressorAdvice.recommendations(both, parameters.engineCompressors))
    }

    private val stages = listOf(CompressorStage(1000.0, 1000.0, 800.0), CompressorStage(1000.0, 1500.0, 1200.0))
    private val models = mapOf(1 to PistonModels(PistonMilitaryModel(stages, 3000.0), stages.reversed(), null))
    private val telemetry = TelemetryParser.parse("""{"valid":true,"H, m":1000,"TAS, km/h":0,"throttle 1, %":100,"compressor stage 1":1}""",
        """{"valid":true,"type":"test"}""")!!

    @Test fun militaryWepAndEqualPowerRespectActualOneBasedStage() {
        assertEquals(listOf(CompressorRecommendation(1, 1, 2)), CompressorAdvice.recommendations(telemetry, models))
        val wep = telemetry.copy(engines = telemetry.engines.map { it.copy(throttlePercent = 110.0) })
        assertTrue(CompressorAdvice.recommendations(wep, models).isEmpty())
        assertTrue(CompressorAdvice.recommendations(wep, models.mapValues { it.value.copy(wepStages = null) }).isEmpty())
        val equal = models.mapValues { it.value.copy(military = PistonMilitaryModel(listOf(stages[0], stages[0]), 3000.0)) }
        val second = telemetry.copy(engines = telemetry.engines.map { it.copy(compressorStage = 2.0) })
        assertTrue(CompressorAdvice.recommendations(second, equal).isEmpty())
    }

    @Test fun invalidOrAmbiguousTelemetryCannotRecommendAShift() {
        for (stage in listOf(null, 0.0, 1.5, 3.0, Double.NaN)) {
            assertTrue(CompressorAdvice.recommendations(telemetry.copy(engines = telemetry.engines.map {
                it.copy(compressorStage = stage)
            }), models).isEmpty())
        }
        assertTrue(CompressorAdvice.recommendations(telemetry.copy(tasKmh = null), models).isEmpty())
        assertTrue(CompressorAdvice.recommendations(telemetry.copy(altitudeM = Double.NaN), models).isEmpty())
        assertTrue(CompressorAdvice.recommendations(telemetry.copy(engines = telemetry.engines + telemetry.engines), models).isEmpty())
        assertTrue(CompressorAdvice.recommendations(telemetry.copy(engines = telemetry.engines.map { it.copy(throttlePercent = 99.0) }), models).isEmpty())
    }

    @Test fun threeSecondDelayRepeatsAndClearsOnMissingModelOrGap() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("Vne:r=800")).copy(engineCompressors = models)
        val model = AircraftAlertModel("test", parameters)
        val state = ConnectionState.Flying(telemetry, FlightMetrics())
        val alerts = FlightAlerts()
        fun tick(time: Long, current: AircraftAlertModel? = model) = alerts.updateForAircraft(state, current, time, true)
        assertTrue(tick(0).active.isEmpty())
        assertTrue(tick(2000).active.isEmpty())
        assertEquals(FlightAlert.COMPRESSOR_STAGE, tick(3000).voice)
        assertNull(tick(5000).voice)
        assertEquals(FlightAlert.COMPRESSOR_STAGE, tick(6000).voice)
        assertTrue(tick(6100, null).active.isEmpty())
        assertTrue(tick(6200).active.isEmpty())
        assertTrue(tick(9200).active.isEmpty()) // Gap discards pending duration.
        tick(11200)
        assertEquals(FlightAlert.COMPRESSOR_STAGE, tick(12200).voice)
    }
}
