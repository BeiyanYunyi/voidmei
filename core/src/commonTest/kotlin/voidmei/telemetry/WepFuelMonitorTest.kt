package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*
import voidmei.config.*
import voidmei.recording.*

class WepFuelMonitorTest {
    private val parameters = FlightModelExtractor.extract(BlkParser.parse("""
        Mass { MaxNitro:r=10 }
        Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.5 } }
    """))
    private val model = AircraftAlertModel("test", parameters)
    private val t = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110}""",
        """{"valid":true,"type":"test"}""")!!
    private val input = ConnectionState.Flying(t, FlightMetrics())

    @Test fun delayPreservesObservedConsumptionWithoutExtrapolatingAcrossTheGap() {
        val monitor = WepFuelMonitor()
        fun remaining(time: Long, flight: ConnectionState.Flying = input) =
            (monitor.update(flight, model, time) as ConnectionState.Flying).metrics.wepFuel!!.estimate.maximumRemainingKg
        assertEquals(10.0, remaining(0))
        assertEquals(9.5, remaining(1000))
        assertEquals(ConnectionState.Delayed, monitor.update(ConnectionState.Delayed, null, 2000))
        assertEquals(ConnectionState.Delayed, monitor.update(ConnectionState.Delayed, null, 2500))
        val idle = input.copy(telemetry = t.copy(engines = t.engines.map { it.copy(throttlePercent = 90.0) }))
        assertEquals(9.5, remaining(3500, idle), "No assumption about WEP use during the delay")
        assertEquals(9.5, remaining(4500))
        assertEquals(9.0, remaining(5500))
    }

    @Test fun delayDoesNotCarryConsumptionIntoAnotherSessionModelOrTimeOrigin() {
        for (boundary in listOf(ConnectionState.Connecting, ConnectionState.WaitingForFlight,
            ConnectionState.Disconnected("offline"))) {
            val monitor = WepFuelMonitor()
            monitor.update(input, model, 0)
            monitor.update(input, model, 1000)
            monitor.update(ConnectionState.Delayed, model, 2000)
            monitor.update(boundary, model, 2100)
            val next = monitor.update(input, model, 3000) as ConnectionState.Flying
            assertEquals(10.0, next.metrics.wepFuel!!.estimate.maximumRemainingKg)
        }
        for (change in listOf("aircraft", "model", "time", "missing")) {
            val monitor = WepFuelMonitor()
            monitor.update(input, model, 0)
            monitor.update(input, model, 1000)
            monitor.update(ConnectionState.Delayed, model, 2000)
            val changedModel = when (change) {
                "aircraft" -> model.copy(aircraft = "other")
                "model" -> model.copy(parameters = parameters.copy(wepFuel = parameters.wepFuel!!.copy(capacityKg = 20.0)))
                else -> model
            }
            if (change == "missing") monitor.update(input, null, 2200)
            val changedInput = if (change == "aircraft") input.copy(telemetry = t.copy(aircraft = "other")) else input
            val next = monitor.update(changedInput, changedModel, if (change == "time") 500 else 3000) as ConnectionState.Flying
            assertEquals(if (change == "model") 20.0 else 10.0, next.metrics.wepFuel!!.estimate.maximumRemainingKg)
        }
    }

    @Test fun pipelineProvidesMatchingHudAndRecordedUpperBoundsAndClearsUnavailableModels() {
        val monitor = WepFuelMonitor()
        val first = monitor.update(input, model, 0) as ConnectionState.Flying
        val next = monitor.update(input, model, 1000) as ConnectionState.Flying
        assertEquals(9.5, HudField.WEP_FUEL.value(next, model))
        assertEquals(19.0, HudField.WEP_TIME.value(next, model))
        assertNull(HudField.WEP_FUEL.value(next, model.copy(aircraft = "other")))
        assertNull(HudField.WEP_FUEL.value(next.copy(telemetry = t.copy(aircraft = "other")), model))
        assertNull(HudField.WEP_FUEL.value(next, model.copy(parameters = parameters.copy(wepFuel = null))))
        val missing = monitor.update(input, null, 2000) as ConnectionState.Flying
        assertNull(missing.metrics.wepFuel)
        val csv = FlightCsv.flightHeader + "\n" + listOf(first, next, missing).mapIndexed { i, f ->
            FlightCsv.flightRow(i.toLong(), i * 1000L, i * 1000L, f)
        }.joinToString("\n")
        val replay = RecordedReplay(csv)
        assertEquals(10.0, replay.frame(0).values["wep_fuel_upper_kg"])
        assertEquals(19.0, replay.frame(1).values["wep_time_upper_s"])
        assertNull(replay.frame(2).values["wep_fuel_upper_kg"])
        val cropped = RecordedReplay(FlightRecordWindow.analyze(csv, 1000, 2000).text)
        assertEquals(9.5, cropped.frame(0).values["wep_fuel_upper_kg"])
        assertNull(cropped.frame(1).values["wep_time_upper_s"])
        val old = csv.lines().joinToString("\n") { it.split(',').take(44).joinToString(",") }
        assertNull(RecordedReplay(old).frame(0).values["wep_fuel_upper_kg"])
    }

    @Test fun nonFlyingResetsAndOldSwitchesRoundTrip() {
        val monitor = WepFuelMonitor()
        monitor.update(input, model, 0)
        monitor.update(input, model, 1000)
        monitor.update(ConnectionState.WaitingForFlight, model, 2000)
        val restored = monitor.update(input, model, 3000) as ConnectionState.Flying
        assertEquals(10.0, HudField.WEP_FUEL.value(restored, model))
        val imported = LegacySettingsReader.read("""(panel p
            (item fuel :type data :target getWepKg :value true)
            (item time :type data :target getWepTime :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("fuel")))
        assertEquals(listOf("fuel", "wep_fuel", "wep_time"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
}
