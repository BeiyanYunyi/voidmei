package voidmei.telemetry

import kotlin.test.*
import voidmei.recording.FlightCsv

class EngineControlsTest {
    private fun parse(fields: String) = TelemetryParser.parse("""{"valid":true,$fields}""", """{"valid":true,"type":"test"}""")!!

    @Test fun controlsRetainEngineIdentityAndUnits() {
        val engines = parse("""
            "RPM throttle 1, %":100,"mixture 1, %":80,"radiator 1, %":40,
            "oil radiator 1, %":20,"compressor stage 1":2,"magneto 1":3,
            "manifold pressure 1, atm":2.24,"pitch 1, deg":35.5,"efficiency 1, %":87,
            "manifold pressure 10, atm":1.5
        """.trimIndent()).engines
        assertEquals(listOf(1, 10), engines.map { it.index })
        val first = engines.first()
        assertEquals(100.0, first.rpmControlPercent)
        assertEquals(80.0, first.mixturePercent)
        assertEquals(40.0, first.radiatorPercent)
        assertEquals(20.0, first.oilRadiatorPercent)
        assertEquals(2.0, first.compressorStage)
        assertEquals(3.0, first.magneto)
        assertEquals(2.24, first.manifoldPressureAtm)
        assertEquals(35.5, first.propellerPitchDeg)
        assertEquals(87.0, first.efficiencyPercent)
        assertEquals(1.5, engines.last().manifoldPressureAtm)
        assertNull(engines.last().rpmControlPercent)
    }

    @Test fun automaticPropellerMissingControlsNeverBecomeSentinelValues() {
        val engine = parse(""""throttle 1, %":110,"RPM throttle 1, %":-65535,"mixture 1, %":null""").engines.single()
        assertNull(engine.rpmControlPercent)
        assertNull(engine.mixturePercent)
        assertNull(engine.compressorStage)
        assertEquals(110.0, engine.throttlePercent)
    }

    @Test fun engineCsvIncludesControlsInTheirNamedColumns() {
        val engines = parse(""""pitch 3, deg":35.5,"manifold pressure 3, atm":2.24""").engines
        val row = FlightCsv.engineRows(1, 1000, engines).single().split(',')
        val columns = FlightCsv.engineHeader.split(',')
        assertEquals(columns.size, row.size)
        assertEquals("35.5", row[columns.indexOf("propeller_pitch_deg")])
        assertEquals("2.24", row[columns.indexOf("manifold_pressure_atm")])
        assertEquals("", row[columns.indexOf("mixture_percent")])
    }
}
