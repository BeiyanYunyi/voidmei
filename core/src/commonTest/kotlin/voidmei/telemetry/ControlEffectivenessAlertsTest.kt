package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class ControlEffectivenessAlertsTest {
    @Test fun equalElevatorPairRestoresBoundAircraftAlertAtExactThreshold() {
        for (document in listOf(
            FlightModelDocument.parse("""{"ElevatorsEffectiveSpeed":[500.5,500.5]}"""),
            BlkParser.parse("ElevatorsEffectiveSpeed:p2=500.5,500.5"),
        )) {
            val parameters = FlightModelExtractor.extract(document)
            assertEquals(500.5, parameters.controlSpeeds.elevatorKmh)
            assertTrue(parameters.issues.none { it.contains("ElevatorsEffectiveSpeed") })
            val bound = AircraftAlertModel("test", parameters)
            val alerts = FlightAlerts()
            assertTrue(alerts.updateForAircraft(flight(500.49), bound, 0, true).active.isEmpty())
            assertEquals(elevator, alerts.updateForAircraft(flight(500.5), bound, 1000, true).voice)
            assertTrue(alerts.updateForAircraft(flight(499.0), bound, 2000, true).active.isEmpty())
        }
    }

    @Test fun unequalOrMalformedPairsNeverInventThresholds() {
        for (value in listOf("[500,600]", "[500,500.0001]", "[0,0]", "[-5,-5]",
            "[500]", "[500,500,500]", "[500,null]", "[\"500\",\"500\"]")) {
            val parameters = FlightModelExtractor.extract(FlightModelDocument.parse(
                """{"ElevatorsEffectiveSpeed":$value,"AileronEffectiveSpeed":400}"""))
            assertNull(parameters.controlSpeeds.elevatorKmh, value)
            assertEquals(400.0, parameters.controlSpeeds.aileronKmh)
            assertTrue(parameters.issues.any { it.contains("ElevatorsEffectiveSpeed") }, value)
        }
        val ambiguous = FlightModelExtractor.extract(BlkParser.parse(
            "ElevatorsEffectiveSpeed:p2=500,500; ElevatorsEffectiveSpeed:r=500"))
        assertNull(ambiguous.controlSpeeds.elevatorKmh)
        val unrelated = FlightModelExtractor.extract(BlkParser.parse("AileronEffectiveSpeed:p2=400,400"))
        assertNull(unrelated.controlSpeeds.aileronKmh)
    }

    private val aileron = FlightAlert.AILERON_EFFECTIVENESS
    private val elevator = FlightAlert.ELEVATOR_EFFECTIVENESS
    private val rudder = FlightAlert.RUDDER_EFFECTIVENESS
    private val speeds = ControlEffectiveSpeeds(400.5, 500.0, 600.0)
    private fun flight(speed: Double?) = ConnectionState.Flying(
        TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", iasKmh = speed), FlightMetrics())
    private fun FlightAlerts.sample(time: Long, speed: Double? = 600.0, disabled: Set<String> = emptySet()) =
        update(flight(speed), null, time, true, disabled, controlSpeeds = speeds)

    @Test fun extractsIndependentPositiveFieldsWithoutTruncatingSpeeds() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("""
            AileronEffectiveSpeed:r=400.5
            ElevatorsEffectiveSpeed:r=500
            RudderEffectiveSpeed:r=600
        """))
        assertEquals(speeds, parameters.controlSpeeds)
        val malformed = FlightModelExtractor.extract(BlkParser.parse("""
            AileronEffectiveSpeed:r=-1
            ElevatorsEffectiveSpeed:t="500"
            One { RudderEffectiveSpeed:r=600 }
            Two { RudderEffectiveSpeed:r=700 }
        """))
        assertEquals(ControlEffectiveSpeeds(), malformed.controlSpeeds)
        assertEquals(3, malformed.issues.count { it.contains("EffectiveSpeed") })
    }

    @Test fun eachExactThresholdIsIndependentAndUnknownIsQuiet() {
        assertTrue(FlightAlerts().sample(0, 400.49).active.isEmpty())
        assertEquals(listOf(aileron), FlightAlerts().sample(0, 400.5).active)
        assertEquals(listOf(aileron, elevator), FlightAlerts().sample(0, 500.0).active)
        assertEquals(listOf(aileron, elevator, rudder), FlightAlerts().sample(0, 600.0).active)
        for (speed in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertTrue(FlightAlerts().sample(0, speed).active.isEmpty())
        assertTrue(FlightAlerts().update(flight(600.0), null, 0, true).active.isEmpty())
    }

    @Test fun simultaneousCrossingsQueueAndDoNotRepeatWhileStillActive() {
        val evaluator = FlightAlerts()
        assertEquals(aileron, evaluator.sample(0).voice)
        assertNull(evaluator.sample(1000).voice)
        assertEquals(elevator, evaluator.sample(2000).voice)
        assertEquals(rudder, evaluator.sample(4000).voice)
        for (time in 6000L..20000L step 2000) {
            val result = evaluator.sample(time)
            assertEquals(listOf(aileron, elevator, rudder), result.active)
            assertNull(result.voice)
        }
        evaluator.sample(21000, 300.0)
        assertEquals(aileron, evaluator.sample(22000).voice)
    }

    @Test fun pendingVoiceExpiresWhenConditionClearsAndDisabledVoiceCanBeEnabled() {
        val evaluator = FlightAlerts()
        assertEquals(elevator, evaluator.sample(0, disabled = setOf("aileronEff")).voice)
        assertEquals(aileron, evaluator.sample(2000).voice)
        // Rudder has not spoken; dropping below its threshold must discard it.
        assertNull(evaluator.sample(4000, 550.0).voice)
        assertEquals(rudder, evaluator.sample(6000).voice)
        evaluator.update(ConnectionState.Disconnected("test"), null, 6100, true)
        assertEquals(aileron, evaluator.sample(6200).voice)
    }

    @Test fun rapidRecrossWaitsForCooldownAndUnknownModelCannotLeakWarnings() {
        val evaluator = FlightAlerts()
        assertEquals(aileron, evaluator.sample(0, 450.0).voice)
        evaluator.sample(1000, 300.0)
        for (time in 2000L..8000L step 2000) assertNull(evaluator.sample(time, 450.0).voice)
        assertEquals(aileron, evaluator.sample(10000, 450.0).voice)
        val bound = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("AileronEffectiveSpeed:r=400")))
        val other = flight(600.0).let { it.copy(telemetry = it.telemetry.copy(aircraft = "other")) }
        assertTrue(evaluator.updateForAircraft(other, bound, 11000, true).active.isEmpty())
        assertEquals(aileron, evaluator.updateForAircraft(flight(600.0), bound, 12000, true).voice)
        assertTrue(evaluator.updateForAircraft(flight(600.0), null, 13000, true).active.isEmpty())
    }
}
