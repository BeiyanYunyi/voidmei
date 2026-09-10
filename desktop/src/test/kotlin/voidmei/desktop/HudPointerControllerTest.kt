package voidmei.desktop

import kotlin.test.*

class HudPointerControllerTest {
    @Test fun failureAfterChangingTheWindowStillRestoresInput() {
        var nativeEnabled = false
        val errors = mutableListOf<String?>()
        val controller = HudPointerController({ value ->
            nativeEnabled = value
            if (value) error("result query failed")
        }, errors::add)
        assertFalse(controller.set(true))
        assertTrue(nativeEnabled)
        assertTrue(controller.needsRestore)
        assertContains(assertNotNull(errors.last()), "result query failed")
        assertTrue(controller.set(false))
        assertFalse(nativeEnabled)
        assertFalse(controller.needsRestore)
        assertNull(errors.last())
    }

    @Test fun failedRestoreRemainsRetryable() {
        var failRestore = true
        val calls = mutableListOf<Boolean>()
        val controller = HudPointerController({ value ->
            calls += value
            if (!value && failRestore) throw UnsatisfiedLinkError("native support unavailable")
        }, {})
        assertTrue(controller.set(true))
        assertFalse(controller.set(false))
        assertTrue(controller.needsRestore)
        failRestore = false
        assertTrue(controller.set(false))
        assertFalse(controller.needsRestore)
        assertEquals(listOf(true, false, false), calls)
        assertTrue(controller.set(false))
        assertEquals(3, calls.size)
    }

    @Test fun defaultInteractiveStateDoesNotLoadNativeSupport() {
        val controller = HudPointerController({ error("must not load native support") }, {})
        assertTrue(controller.set(false))
        assertFalse(controller.needsRestore)
    }
}
