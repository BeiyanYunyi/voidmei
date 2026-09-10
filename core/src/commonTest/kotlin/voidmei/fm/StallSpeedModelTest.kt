package voidmei.fm

import kotlin.test.*
import kotlin.math.sqrt
import voidmei.telemetry.*

class StallSpeedModelTest {
    private val source = """
        EmptyMass:r=900; OilMass:r=75; MaxNitro:r=25
        Areas {
            WingLeftIn:r=5; WingLeftMid:r=0; WingLeftOut:r=0; WingLeftCut:r=0
            WingRightIn:r=5; WingRightMid:r=0; WingRightOut:r=0; WingRightCut:r=0
            Aileron:r=0; Fuselage:r=2
        }
        NoFlaps { alphaCritHigh:r=10; ClCritHigh:r=1 }
        FullFlaps { alphaCritHigh:r=20; ClCritHigh:r=2 }
        Fuselage { alphaCritHigh:r=15; ClCritHigh:r=1; ClAfterCrit:r=0.5; lineClCoeff:r=1 }
    """

    @Test fun polarFallbackSelectsWholeBlocksAndReportsMissingFields() {
        val mixedWing = source.replace("NoFlaps { alphaCritHigh:r=10; ClCritHigh:r=1 }",
            "NoFlaps { alphaCritHigh:r=10 } FlapsPolar0 { ClCritHigh:r=1 }")
        val wing = FlightModelExtractor.extract(BlkParser.parse(mixedWing))
        assertNull(wing.stallSpeed)
        assertTrue(wing.stallSpeedIssue!!.contains("NoFlaps.ClCritHigh"))
        val mixedBody = source.replace("ClAfterCrit:r=0.5;", "") +
            " FuselagePlane { Polar { ClAfterCrit:r=0.5 } }"
        val body = FlightModelExtractor.extract(BlkParser.parse(mixedBody))
        assertNull(body.stallSpeed)
        assertTrue(body.stallSpeedIssue!!.contains("Fuselage.ClAfterCrit"))
        val alternate = source.replace("NoFlaps {", "FlapsPolar0 {").replace("FullFlaps {", "FlapsPolar1 {")
        val complete = FlightModelExtractor.extract(BlkParser.parse(alternate))
        assertNotNull(complete.stallSpeed)
        assertNull(complete.stallSpeedIssue)
    }

    @Test fun nestedFieldsCannotFillHolesInASelectedPolarBlock() {
        val nested = source.replace("NoFlaps { alphaCritHigh:r=10; ClCritHigh:r=1 }",
            "NoFlaps { alphaCritHigh:r=10 } Other { NoFlaps { ClCritHigh:r=1 } }")
        val result = FlightModelExtractor.extract(BlkParser.parse(nested))
        assertNull(result.stallSpeed)
        assertTrue(result.stallSpeedIssue!!.contains("NoFlaps.ClCritHigh"))
        val duplicate = source + " NoFlaps { ClCritHigh:r=1 }"
        assertTrue(FlightModelExtractor.extract(BlkParser.parse(duplicate)).stallSpeedIssue!!.contains("Ambiguous block"))
        val negativeBody = FlightModelExtractor.extract(BlkParser.parse(source.replace("ClAfterCrit:r=0.5", "ClAfterCrit:r=-0.5")))
        assertTrue(negativeBody.stallSpeedIssue!!.contains("Fuselage.ClAfterCrit"))
        val missingMass = FlightModelExtractor.extract(BlkParser.parse(source.replace("OilMass:r=75;", "")))
        assertTrue(missingMass.stallSpeedIssue!!.contains("OilMass"))
    }

    @Test fun extractsSeparateBodyContributionsAndMatchesHandCalculatedSpeed() {
        val model = assertNotNull(FlightModelExtractor.extract(BlkParser.parse(source)).stallSpeed)
        // Clean body uses ClCritHigh; full flaps uses ClAfterCrit independently.
        assertEquals(10 + 2.0 * 10 / 15, model.profiles.single().cleanArea, 1e-10)
        assertEquals(20 + 2.0 * .5 * 20 / 15, model.profiles.single().fullFlapArea, 1e-10)
        val expected = 3.6 * sqrt(2 * 2000 * 9.80 / (1.225 * (15 + 4.0 / 3)))
        assertEquals(expected, assertNotNull(model.speedKmh(1000.0, 50.0, null)), 1e-10)
        assertTrue(model.speedKmh(0.0, 50.0, null)!! < expected)
        assertTrue(model.speedKmh(1000.0, 100.0, null)!! < model.speedKmh(1000.0, 0.0, null)!!)
    }

    @Test fun unknownAndInvalidGeometryDoNotBecomeZeroLiftOrRetainedSpeed() {
        for (bad in listOf(source.replace("OilMass:r=75;", ""),
            source.replace("WingLeftIn:r=5", "WingLeftIn:r=-5"),
            source.replace("alphaCritHigh:r=15", "alphaCritHigh:r=0"),
            source + "\nEmptyMass:r=1000"))
            assertNull(FlightModelExtractor.extract(BlkParser.parse(bad)).stallSpeed)
        val model = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0)))
        for (bad in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(model.speedKmh(bad, 50.0, null))
        for (bad in listOf(null, -1.0, 101.0, Double.NaN)) assertNull(model.speedKmh(100.0, bad, null))
    }

    @Test fun sweepAndFlapsInterpolateLiftBeforeComputingSpeed() {
        val model = StallSpeedModel(1000.0, listOf(StallLiftProfile(0.0, 10.0, 20.0), StallLiftProfile(1.0, 6.0, 12.0)))
        assertNull(model.speedKmh(0.0, 50.0, null))
        assertEquals(3.6 * sqrt(2 * 1000 * 9.80 / (1.225 * 12)), model.speedKmh(0.0, 50.0, .5)!!, 1e-10)
        val profile = """Areas { LeftIn:r=5; LeftMid:r=0; LeftOut:r=0; LeftCut:r=0; RightIn:r=5; RightMid:r=0; RightOut:r=0; RightCut:r=0; Aileron:r=0 }
            FlapsPolar0 { alphaCritHigh:r=10; ClCritHigh:r=1 }
            FlapsPolar1 { alphaCritHigh:r=20; ClCritHigh:r=2 }"""
        val swept = """EmptyMass:r=1000; OilMass:r=0; MaxNitro:r=0; Areas { Fuselage:r=0 }
            WingPlaneSweep0 { Sweep:r=0; $profile }
            WingPlaneSweep1 { Sweep:r=1; ${profile.replace("In:r=5", "In:r=3")} }"""
        assertEquals(model, FlightModelExtractor.extract(BlkParser.parse(swept)).stallSpeed)
    }

    @Test fun warningUsesCurrentModelAndLegacyGearAndVerticalMotionGate() {
        val bound = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse(source)))
        val threshold = bound.parameters.stallSpeed!!.speedKmh(1000.0, 50.0, null)!!
        fun warning(speed: Double?, gear: Double? = 0.0, climb: Double? = 1.0, aircraft: String? = "test") =
            FlightAlerts().updateForAircraft(ConnectionState.Flying(
                TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
                    .copy(aircraft = aircraft, iasKmh = speed, gearPercent = gear, verticalSpeedMps = climb,
                        fuelKg = 1000.0, flapsPercent = 50.0), FlightMetrics()), bound, 0, true)
        assertEquals(FlightAlert.STALL_SPEED, warning(threshold).voice)
        assertEquals(FlightAlert.STALL_SPEED, warning(threshold, climb = -1.0).voice)
        assertTrue(warning(threshold + .01).active.isEmpty())
        assertTrue(warning(threshold, climb = 0.0).active.isEmpty())
        assertTrue(warning(threshold, gear = 1.0).active.isEmpty())
        assertTrue(warning(threshold, aircraft = "other").active.isEmpty())
        assertTrue(warning(null).active.isEmpty())
    }
}
