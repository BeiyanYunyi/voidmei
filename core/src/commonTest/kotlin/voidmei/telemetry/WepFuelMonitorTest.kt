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
