package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class WepFuelTest {
    private val document = BlkParser.parse("""
        Mass { MaxNitro:r=10 }
        EngineType0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.5 } }
        Engine0 { Type:i=0 }
        Engine2 { Type:i=0; Afterburner { NitroConsumption:r=1.5 } }
    """)
    private val model = WepFuelExtractor.extract(document)!!
    private val t = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110,"throttle 3, %":100}""",
        """{"valid":true,"type":"test"}""")!!

    @Test fun extractsInstanceRatesAndRejectsAmbiguousOrMissingParameters() {
        assertEquals(WepFuelModel(10.0, mapOf(1 to 0.5, 3 to 1.5)), model)
        assertEquals(model, FlightModelExtractor.extract(document).wepFuel)
        for (source in listOf(
            "Mass { MaxNitro:r=0 } Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=1 } }",
            "Mass { MaxNitro:r=10 } Engine0 { Main { Type:t=Inline } }",
            "Mass { MaxNitro:r=10; MaxNitro:r=20 } Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=1 } }",
            "Mass { MaxNitro:r=10 } Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=-1 } }"
        )) assertNull(WepFuelExtractor.extract(BlkParser.parse(source)))
    }

    @Test fun integratesPreviousEngineStatesAndDividesByCurrentCombinedRate() {
        val tracker = WepFuelTracker()
        assertEquals(WepFuelEstimate(10.0, 20.0), tracker.update(t, model, 0))
        val both = t.copy(engines = t.engines.map { it.copy(throttlePercent = 110.0) }.reversed())
        assertEquals(WepFuelEstimate(9.5, 4.75), tracker.update(both, model, 1000))
        val off = t.copy(engines = t.engines.map { it.copy(throttlePercent = 100.0) })
        assertEquals(WepFuelEstimate(7.5, null), tracker.update(off, model, 2000))
        assertEquals(WepFuelEstimate(7.5, 3.75), tracker.update(both, model, 3000))
        tracker.update(both, model, 5000)
        assertEquals(WepFuelEstimate(0.0, 0.0), tracker.update(both, model, 7000))
    }

    @Test fun interruptionsRestoreUnknownInitialBoundRatherThanClaimingMeasuredRefill() {
        val tracker = WepFuelTracker()
        tracker.update(t, model, 0)
        assertEquals(9.5, tracker.update(t, model, 1000)!!.maximumRemainingKg)
        assertEquals(10.0, tracker.update(t, model, 4000)!!.maximumRemainingKg)
        assertNull(tracker.update(t.copy(engines = t.engines.take(1)), model, 5000))
        assertEquals(10.0, tracker.update(t, model, 6000)!!.maximumRemainingKg)
        assertNull(tracker.update(t, null, 7000))
        assertEquals(10.0, tracker.update(t.copy(aircraft = "other"), model, 8000)!!.maximumRemainingKg)
    }
}
