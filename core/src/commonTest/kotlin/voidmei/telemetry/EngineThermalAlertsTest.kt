package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class EngineThermalAlertsTest {
    private val model = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("""
        Engine0 { Main { Type:t=Inline }
            Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=302; RecoverTime:r=2 } }
        }
        Engine1 { Main { Type:t=Inline }
            Temperature { Load1 { OilTemperature:r=100; WorkTime:r=302; RecoverTime:r=2 } }
        }
    """)))
    private val hot = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true,"water temp 1, C":110,"oil temp 2, C":110}""",
        """{"valid":true,"type":"test"}""")!!, FlightMetrics())

    @Test fun strictUpperBoundThresholdAndPerEngineChannels() {
        val monitor = EngineThermalMonitor()
        val alerts = FlightAlerts()
        fun tick(time: Long): AlertUpdate {
            val observation = monitor.update(hot, model, time)
            return alerts.updateForAircraft(hot, model, time, true, thermalObservation = observation)
        }
        assertTrue(tick(0).active.isEmpty()) // Lower bound zero alone cannot trigger.
        assertTrue(tick(2000).active.isEmpty()) // Exactly 300 seconds.
        assertEquals(FlightAlert.ENGINE_OVERHEAT, tick(2100).voice)
        val observation = monitor.update(hot, model, 2200)!!
        assertEquals(setOf(1, 2), observation.warningEngines(hot, model))
        val partial = hot.copy(telemetry = hot.telemetry.copy(engines = hot.telemetry.engines.map {
            if (it.index == 1) it.copy(waterTemperatureC = null) else it
        }))
        assertEquals(setOf(2), monitor.update(partial, model, 2300)!!.warningEngines(partial, model))
        assertTrue(observation.warningEngines(partial, model).isEmpty())
        assertTrue(observation.warningEngines(hot, null).isEmpty())
    }

    @Test fun coolingDisconnectAndVoiceCooldown() {
        val monitor = EngineThermalMonitor()
        val alerts = FlightAlerts()
        fun tick(time: Long, state: ConnectionState = hot, enabled: Boolean = true, disabled: Set<String> = emptySet()): AlertUpdate =
            alerts.updateForAircraft(state, model, time, enabled, disabled, monitor.update(state, model, time))
        tick(0)
        tick(2000)
        assertEquals(FlightAlert.ENGINE_OVERHEAT, tick(4000).voice)
        for (time in 6000L..62000L step 2000) {
            val update = tick(time)
            assertEquals(listOf(FlightAlert.ENGINE_OVERHEAT), update.active)
            assertNull(update.voice)
        }
        assertEquals(FlightAlert.ENGINE_OVERHEAT, tick(64000).voice)
        val cool = hot.copy(telemetry = hot.telemetry.copy(engines = hot.telemetry.engines.map {
            it.copy(waterTemperatureC = 90.0, oilTemperatureC = 90.0)
        }))
        assertTrue(tick(66000, cool).active.isEmpty())
        tick(68000, cool)
        assertTrue(tick(70000).active.isEmpty()) // Fully recovered before heating again.
        assertTrue(tick(70100, ConnectionState.Disconnected("test")).active.isEmpty())
        assertTrue(tick(70200).active.isEmpty())
        tick(72200)
        val muted = tick(74200, enabled = false)
        assertEquals(listOf(FlightAlert.ENGINE_OVERHEAT), muted.active)
        assertNull(muted.voice)
        assertNull(tick(76200, disabled = setOf("warn_engineoverheat")).voice)
        assertEquals(FlightAlert.ENGINE_OVERHEAT, tick(78200).voice)
    }
}
