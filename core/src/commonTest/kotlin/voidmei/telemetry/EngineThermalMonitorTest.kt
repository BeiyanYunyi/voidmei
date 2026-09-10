package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class EngineThermalMonitorTest {
    private val parameters = FlightModelExtractor.extract(BlkParser.parse("""
        Engine0 { Main { Type:t=Inline }
            Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=10; RecoverTime:r=5 } }
        }
    """))
    private val model = AircraftAlertModel("test", parameters)
    private val telemetry = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110}""",
        """{"valid":true,"type":"test"}""")!!
    private val flight = ConnectionState.Flying(telemetry, FlightMetrics())
    private fun EngineThermalObservation.maximum() = budgets.single().water!!.activeRemaining!!.maximumSeconds

    @Test fun flightLifecycleResetsEvenForBriefInterruptions() {
        val monitor = EngineThermalMonitor()
        assertEquals(10.0, monitor.update(flight, model, 0)!!.maximum())
        assertEquals(8.0, monitor.update(flight, model, 2000)!!.maximum())
        for (state in listOf(ConnectionState.Connecting, ConnectionState.WaitingForFlight, ConnectionState.Disconnected("test"))) {
            assertNull(monitor.update(state, model, 2100))
            assertEquals(10.0, monitor.update(flight, model, 2200)!!.maximum())
        }
        assertNull(monitor.update(flight, null, 2300))
        assertEquals(10.0, monitor.update(flight, model, 2400)!!.maximum())
        assertNull(monitor.update(flight, model.copy(aircraft = "other"), 2500))
        assertNull(monitor.update(flight.copy(telemetry = telemetry.copy(aircraft = null)), model, 2600))
    }

    @Test fun observationCannotSurviveModelWithdrawalOrDisplayWithAnotherFrame() {
        val observation = EngineThermalMonitor().update(flight, model, 0)!!
        assertEquals(1, observation.budgetsFor(flight, model).size)
        assertTrue(observation.budgetsFor(flight, null).isEmpty())
        assertTrue(observation.budgetsFor(flight, model.copy(aircraft = "other")).isEmpty())
        assertTrue(observation.budgetsFor(ConnectionState.Connecting, model).isEmpty())
        assertTrue(observation.budgetsFor(flight.copy(telemetry = telemetry.copy(iasKmh = 250.0)), model).isEmpty())
        val changed = model.copy(parameters = parameters.copy(engineThermals = parameters.engineThermals.map {
            it.copy(bands = it.bands.map { band -> band.copy(workSeconds = 20.0) })
        }))
        assertTrue(observation.budgetsFor(flight, changed).isEmpty())
    }
}
