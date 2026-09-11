package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*
import voidmei.config.*

class ThermalHudTest {
    @Test fun budgetsFollowSelectedEngineAndDoNotFallbackToEngineOne() {
        val second = parameters.engineThermals.single().copy(telemetryIndex = 2,
            bands = parameters.engineThermals.single().bands.map { it.copy(workSeconds = 400.0) })
        val bothModel = model.copy(parameters = parameters.copy(engineThermals = parameters.engineThermals + second))
        val both = flight(t.copy(engines = t.engines + t.engines.single().copy(index = 2)))
        val observation = EngineThermalMonitor().update(both, bothModel, 0)!!
        assertEquals(ThermalBudgetRange(0.0, 10.0), observation.hudBudget(both, bothModel, 1))
        assertEquals(ThermalBudgetRange(0.0, 400.0), observation.hudBudget(both, bothModel, 2))
        assertNull(observation.hudBudget(both, bothModel, 3))
        assertNull(observation.hudBudget(both, bothModel.copy(aircraft = "other"), 2))
    }

    @Test fun displayedIntervalContainsTheUnroundedBudget() {
        assertEquals(ThermalBudgetRange(0.0, 0.1), ThermalBudgetRange(0.0, 0.04).roundForDisplay())
        assertEquals(ThermalBudgetRange(1.2, 6.1), ThermalBudgetRange(1.26, 6.04).roundForDisplay())
        assertEquals(ThermalBudgetRange(0.0, 0.0), ThermalBudgetRange(0.0, 0.0).roundForDisplay())
        for (value in listOf(0.0, 0.001, 1.26, 6.04, 100.001, Double.MAX_VALUE)) {
            val shown = ThermalBudgetRange(value, value).roundForDisplay()!!
            assertTrue(shown.minimumSeconds <= value)
            assertTrue(shown.maximumSeconds >= value)
        }
        for (range in listOf(ThermalBudgetRange(-1.0, 1.0), ThermalBudgetRange(2.0, 1.0),
            ThermalBudgetRange(0.0, Double.POSITIVE_INFINITY), ThermalBudgetRange(Double.NaN, 1.0))) {
            assertNull(range.roundForDisplay())
        }
    }

    private val parameters = FlightModelExtractor.extract(BlkParser.parse("""
        Engine0 { Main { Type:t=Inline }
            Temperature { Load1 { WaterTemperature:r=100; OilTemperature:r=90; WorkTime:r=10; RecoverTime:r=5 } } }
    """))
    private val model = AircraftAlertModel("test", parameters)
    private val t = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110,"oil temp 1, C":95}""",
        """{"valid":true,"type":"test"}""")!!
    private fun flight(t: Telemetry = this.t) = ConnectionState.Flying(t, FlightMetrics())

    @Test fun matchingFramePreservesRangeAndExhaustionWhileRejectingStaleOrPartialData() {
        val monitor = EngineThermalMonitor()
        val first = monitor.update(flight(), model, 0)!!
        assertEquals(ThermalBudgetRange(0.0, 10.0), first.hudBudget(flight(), model))
        assertNull(first.hudBudget(flight(t.copy(aircraft = "other")), model))
        assertNull(first.hudBudget(flight(), null))
        assertNull(first.hudBudget(flight(), model.copy(parameters = parameters.copy(engineThermals = emptyList()))))
        for (time in 2000L..10000L step 2000) monitor.update(flight(), model, time)
        val exhausted = monitor.update(flight(), model, 11000)!!
        assertEquals(ThermalBudgetRange(0.0, 0.0), exhausted.hudBudget(flight(), model))
        val missing = flight(t.copy(engines = t.engines.map { it.copy(oilTemperatureC = null) }))
        assertNull(monitor.update(missing, model, 12000)!!.hudBudget(missing, model))
        val cool = flight(t.copy(engines = t.engines.map { it.copy(waterTemperatureC = 50.0, oilTemperatureC = 50.0) }))
        assertNull(monitor.update(cool, model, 13000)!!.hudBudget(cool, model))
    }

    @Test fun legacyHeatSwitchSurvivesSettingsRoundTrip() {
        val imported = LegacySettingsReader.read("""(panel p (item heat :type data :target getHeatTolerance :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("ias")))
        assertEquals(listOf("ias", "heat_tolerance"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
