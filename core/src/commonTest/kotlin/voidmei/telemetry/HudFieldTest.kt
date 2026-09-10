package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class HudFieldTest {
    @Test fun fuelMassShareUsesMatchingBasicMassAndPreservesZero() {
        val t = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":500}""", """{"valid":true,"type":"test"}""")!!
        val p = voidmei.fm.FlightModelParameters(null, null, emptyList(), false, emptyList(), basicMassKg = 1500.0)
        val model = AircraftAlertModel("test", p)
        fun read(f: Telemetry = t, m: AircraftAlertModel? = model) = HudField.FUEL_MASS_SHARE.value(ConnectionState.Flying(f, FlightMetrics()), m)
        assertEquals(25.0, read())
        assertEquals(0.0, read(t.copy(fuelKg = 0.0)))
        assertEquals(75.0, read(t.copy(fuelKg = 4500.0)))
        assertNull(read(t.copy(aircraft = "other")))
        assertNull(read(m = null))
        for (bad in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(read(t.copy(fuelKg = bad)))
        for (bad in listOf(null, 0.0, -1.0, Double.NaN)) assertNull(read(m = AircraftAlertModel("test", p.copy(basicMassKg = bad))))
        val settings = AppSettings(hudFields = listOf("fuel_mass_share"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }

    @Test fun throttleFieldPreservesBoostAndRequiresUniqueEngineOne() {
        val base = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110,"throttle 2, %":75}""",
            """{"valid":true}""")!!
        fun read(t: Telemetry) = HudField.ENGINE1_THROTTLE.value(ConnectionState.Flying(t, FlightMetrics()))
        assertEquals(110.0, read(base))
        assertEquals(110.0, read(base.copy(engines = base.engines.reversed())))
        val first = base.engines.single { it.index == 1 }
        assertEquals(0.0, read(base.copy(engines = listOf(first.copy(throttlePercent = 0.0)))))
        for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(read(base.copy(engines = listOf(first.copy(throttlePercent = invalid)))))
        assertNull(read(base.copy(engines = base.engines.filter { it.index == 2 })))
        assertNull(read(base.copy(engines = listOf(first, first))))
        val settings = AppSettings(hudFields = listOf("future", "engine1_throttle"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFalse("engine1_throttle" in HudField.defaults)
    }

    @Test fun thrustPowerUsesAllReportedEnginesAndDistinguishesZeroFromMissingData() {
        val base = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,
            "thrust 1, kgs":200,"thrust 2, kgs":500}""", """{"valid":true}""")!!
        fun value(telemetry: Telemetry) = HudField.THRUST_POWER.value(
            ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0)))
        assertEquals(686.4655, value(base)!!, 1e-8)
        assertEquals(value(base), value(base.copy(engines = base.engines.reversed())))
        assertEquals(0.0, value(base.copy(tasKmh = 0.0)))
        assertNull(value(base.copy(tasKmh = null)))
        assertNull(value(base.copy(engines = base.engines.map {
            if (it.index == 2) it.copy(thrustKgf = null) else it
        })))
        val imported = LegacySettingsReader.read("""(panel p
            (item effective :type data :target getEffHp :value true))""")
        val settings = imported.applyTo(AppSettings(hudFields = listOf("future", "power")))
        assertEquals(listOf("future", "power", "thrust_power"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFalse("thrust_power" in HudField.defaults)
    }

    @Test fun engineOneFieldsUseStableIndexWithoutBorrowingFromOtherEngines() {
        val base = TelemetryParser.parse("""{"valid":true,"thrust 1, kgs":100,"RPM 1":2400,
            "pitch 1, deg":-5.5,"thrust 2, kgs":900,"RPM 2":3000,"pitch 2, deg":40}""",
            """{"valid":true}""")!!
        fun flight(engines: List<Engine>) = ConnectionState.Flying(base.copy(engines = engines), FlightMetrics(totalThrustKgf = 1000.0))
        val reversed = flight(base.engines.reversed())
        assertEquals(100.0, HudField.ENGINE1_THRUST.value(reversed))
        assertEquals(2400.0, HudField.ENGINE1_RPM.value(reversed))
        assertEquals(-5.5, HudField.ENGINE1_PITCH.value(reversed))
        assertEquals(1000.0, HudField.THRUST.value(reversed))
        val fields = listOf(HudField.ENGINE1_THRUST, HudField.ENGINE1_RPM, HudField.ENGINE1_PITCH)
        for (engines in listOf(base.engines.filter { it.index != 1 }, base.engines + base.engines.first { it.index == 1 },
            base.engines.map { it.copy(thrustKgf = null, rpm = null, propellerPitchDeg = null) })) {
            fields.forEach { assertNull(it.value(flight(engines)), it.id) }
        }
        val imported = LegacySettingsReader.read("""(panel p
            (item thrust :type data :target getThrust :value true)
            (item rpm :type data :target getRPM :value true)
            (item pitch :type data :target getPitch :value true))""").applyTo(AppSettings(hudFields = listOf("thrust")))
        assertEquals(listOf("thrust") + fields.map { it.id }, imported.hudFields)
        assertEquals(imported, SettingsJson.decode(SettingsJson.encode(imported)))
    }

    @Test fun sweepImportPreservesSelectionAndShowsPercentWithoutInventingMissingData() {
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
        for ((ratio, expected) in listOf(0.0 to 0.0, 0.375 to 37.5, 1.0 to 100.0)) {
            assertEquals(expected, HudField.WING_SWEEP.value(ConnectionState.Flying(
                base.copy(wingSweepRatio = ratio), FlightMetrics())))
        }
        for (ratio in listOf(null, -65535.0, -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(HudField.WING_SWEEP.value(ConnectionState.Flying(base.copy(wingSweepRatio = ratio), FlightMetrics())))
        }
        fun imported(enabled: Boolean) = LegacySettingsReader.read(
            """(panel p (item sweep :type data :target "getWingSweep * 100" :value $enabled))""")
        val settings = imported(true).applyTo(AppSettings(hudFields = listOf("future", "ias")))
        assertEquals(listOf("future", "ias", "wing_sweep"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf("future", "ias"), imported(false).applyTo(settings).hudFields)
        assertFalse("wing_sweep" in HudField.defaults)
    }

    @Test fun controlSurfaceFieldsPreserveSignedPercentagesAndPersistOrder() {
        val t = TelemetryParser.parse("""{"valid":true,"aileron, %":-75.5,"elevator, %":25.0,"rudder, %":0}""",
            """{"valid":true}""")!!
        val flight = ConnectionState.Flying(t, FlightMetrics())
        assertEquals(-75.5, HudField.AILERON.value(flight))
        assertEquals(25.0, HudField.ELEVATOR.value(flight))
        assertEquals(0.0, HudField.RUDDER.value(flight))
        val missing = flight.copy(telemetry = t.copy(aileronPercent = null,
            elevatorPercent = Double.NaN, rudderPercent = Double.POSITIVE_INFINITY))
        listOf(HudField.AILERON, HudField.ELEVATOR, HudField.RUDDER).forEach { assertNull(it.value(missing)) }
        val settings = AppSettings(hudFields = listOf("rudder", "ias", "aileron", "elevator"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(HudField.RUDDER, HudField.IAS, HudField.AILERON, HudField.ELEVATOR), HudField.selected(settings.hudFields))
        assertFalse("aileron" in HudField.defaults)
    }

    @Test fun independentHeadingWrapsAtNorthAndRemainsUnknownWithoutCompass() {
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
        for ((raw, shown) in listOf(-90.0 to 270.0, 360.0 to 0.0, 359.9 to 0.0, 721.0 to 1.0)) {
            assertEquals(shown, HudField.HEADING.value(ConnectionState.Flying(base.copy(headingDeg = raw), FlightMetrics())))
        }
        for (raw in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(HudField.HEADING.value(ConnectionState.Flying(base.copy(headingDeg = raw), FlightMetrics())))
        }
        val imported = LegacySettingsReader.read("""(panel p (item x :type data :target getCompass :value true))""")
            .applyTo(AppSettings(hudFields = listOf("future"), hudAttitude = false))
        assertEquals(listOf("future", "heading"), imported.hudFields)
        assertFalse(imported.hudAttitude)
        assertEquals(imported, SettingsJson.decode(SettingsJson.encode(imported)))
    }

    @Test fun persistedOrderAndUnknownFieldsSurviveRoundTrip() {
        val settings = AppSettings(hudFields = listOf("fuel", "future_field", "ias"), hudAttitude = false, hudMechanization = false)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(HudField.FUEL, HudField.IAS), HudField.selected(settings.hudFields))
    }
    @Test fun emptySelectionIsIntentionalAndDuplicatesRenderOnce() {
        assertTrue(HudField.selected(emptyList()).isEmpty())
        assertTrue(SettingsJson.decode(SettingsJson.encode(AppSettings(hudFields = emptyList()))).hudFields.isEmpty())
        assertEquals(listOf(HudField.IAS), HudField.selected(listOf("ias", "ias")))
    }
    @Test fun previousConfigurationReceivesDefaultFields() {
        val loaded = SettingsJson.decode("""{"version":1}""")
        assertEquals(HudField.defaults, loaded.hudFields)
        assertTrue(loaded.hudAttitude)
        assertTrue(loaded.hudMechanization)
    }
    @Test fun stallFieldRequiresCurrentAircraftAndCurrentConfiguration() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("Vne:r=800"))
            .copy(stallSpeed = voidmei.fm.StallSpeedModel(1000.0, listOf(voidmei.fm.StallLiftProfile(0.0, 16.0, 32.0))))
        val model = AircraftAlertModel("test", parameters)
        val t = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "TEST", fuelKg = 0.0, flapsPercent = 0.0)
        val flight = ConnectionState.Flying(t, FlightMetrics())
        assertEquals(3.6 * kotlin.math.sqrt(1000.0), HudField.STALL_IAS.value(flight, model)!!, 1e-10)
        assertNull(HudField.STALL_IAS.value(flight))
        assertNull(HudField.STALL_IAS.value(flight.copy(telemetry = t.copy(aircraft = "other")), model))
        assertNull(HudField.STALL_IAS.value(flight.copy(telemetry = t.copy(flapsPercent = null)), model))
        assertNull(HudField.STALL_IAS.value(flight.copy(telemetry = t.copy(fuelKg = null)), model))
        val selected = AppSettings(hudFields = listOf("ias", "stall_ias"))
        assertEquals(selected, SettingsJson.decode(SettingsJson.encode(selected)))
    }

    @Test fun fieldValuesApplyUnitsAndPreserveUnknowns() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":360}""", """{"valid":true}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics(fuelEnduranceSeconds = 150.0, totalPowerHp = Double.NaN))
        assertEquals(360.0, HudField.IAS.value(flight))
        assertEquals(2.5, HudField.ENDURANCE.value(flight))
        assertNull(HudField.TAS.value(flight))
        assertNull(HudField.POWER.value(flight))
    }
}
