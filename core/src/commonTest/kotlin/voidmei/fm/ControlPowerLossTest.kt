package voidmei.fm

import kotlin.test.*

class ControlPowerLossTest {
    private fun extract(text: String) = FlightModelExtractor.extract(BlkParser.parse(text))
    @Test fun rawCoefficientsPreserveSignZeroAndExactRootPrecedence() {
        val p = extract("AileronPowerLoss:r=0\nElevatorPowerLoss:r=1.2345\nRudderPowerLoss:r=-0.25\nOther { AileronPowerLoss:r=9 }")
        assertEquals(ControlPowerLoss(0.0, 1.2345, -.25), p.controlPowerLoss)
        assertEquals(ControlPowerLoss(2.0), extract("Other { AileronPowerLoss:i=2 }").controlPowerLoss)
        assertEquals(ControlPowerLoss(), extract("Mass { EmptyMass:r=2500 }").controlPowerLoss)
    }
    @Test fun invalidAndAmbiguousValuesStayUnknownWithIssues() {
        for (source in listOf("AileronPowerLoss:r=NaN", "AileronPowerLoss:r=Infinity", "AileronPowerLoss:t=bad",
            "AileronPowerLoss:p2=1,1", "AileronPowerLoss:r=1\nAileronPowerLoss:r=2",
            "A { AileronPowerLoss:r=1 } B { AileronPowerLoss:r=2 }")) {
            val p = extract(source)
            assertNull(p.controlPowerLoss.aileron)
            assertTrue(p.issues.any { "AileronPowerLoss" in it })
        }
    }
}
