package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class AirflowMarkerTest {
    @Test fun limitsFollowCurrentAircraftFlapsAndSweepAndOmitOutOfScaleValues() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("Vne:r=800")).copy(
            variableSweep = true, wings = listOf(
                WingConfiguration(0.0, null, null, -10.0, 20.0, -8.0, 16.0),
                WingConfiguration(1.0, null, null, -6.0, 12.0, -4.0, 8.0)))
        val model = AircraftAlertModel("test", parameters)
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
            .copy(wingSweepRatio = 0.5, flapsPercent = 50.0)
        assertEquals(listOf(-7.0, 14.0), AirflowLimits.fromTelemetry(telemetry, model))
        assertTrue(AirflowLimits.fromTelemetry(telemetry.copy(aircraft = "other"), model).isEmpty())
        assertTrue(AirflowLimits.fromTelemetry(telemetry.copy(flapsPercent = null), model).isEmpty())
        assertTrue(AirflowLimits.fromTelemetry(telemetry.copy(wingSweepRatio = null), model).isEmpty())
        val out = model.copy(parameters = parameters.copy(variableSweep = false,
            wings = listOf(WingConfiguration(0.0, null, null, -40.0, 40.0, -40.0, 40.0))))
        assertTrue(AirflowLimits.fromTelemetry(telemetry, out).isEmpty())
    }

    @Test fun bodyRelativeDirectionsAndScaleMatchLegacyLocator() {
        assertEquals(AirflowMarker(0.0, 0.0, false), AirflowMarker.fromAngles(0.0, -0.0))
        assertEquals(AirflowMarker(-0.5, 0.5, false), AirflowMarker.fromAngles(15.0, 7.5))
        assertEquals(AirflowMarker(1.0, -1.0, false), AirflowMarker.fromAngles(-30.0, -15.0))
        assertEquals(AirflowMarker(-1.0, 1.0, true), AirflowMarker.fromAngles(60.0, 20.0))
        for (invalid in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(AirflowMarker.fromAngles(invalid, 0.0))
            assertNull(AirflowMarker.fromAngles(0.0, invalid))
        }
    }
}
