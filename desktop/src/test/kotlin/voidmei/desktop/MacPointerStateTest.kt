package voidmei.desktop

import kotlin.test.*
import org.junit.Test

class MacPointerStateTest {
    @Test fun repeatedEnableRestoresTheOriginalPolicyIncludingAnAlreadyTransparentWindow() {
        for (original in listOf(false, true)) {
            var policy = original
            val state = MacPointerState()
            repeat(2) { state.set(1, true, { policy }, { policy = it }) }
            assertTrue(policy)
            state.set(1, false, { policy }, { policy = it })
            assertEquals(original, policy)
            state.set(1, false, { error("already restored") }, { error("already restored") })
        }
    }

    @Test fun failedVerificationAndFailedRestoreKeepTheOriginalPolicyForRetry() {
        var policy = false
        var failedRead = false
        val state = MacPointerState()
        assertFails {
            state.set(2, true, { if (failedRead) error("read failed") else policy }, { policy = it; failedRead = true })
        }
        assertTrue(policy)
        assertFails { state.set(2, false, { policy }, { error("write failed") }) }
        state.set(2, false, { policy }, { policy = it })
        assertFalse(policy)
        assertFails { state.set(2, true, { policy }, { /* rejected by AppKit */ }) }
    }

    @Test fun aRecreatedPeerDoesNotInheritTheOldPeersInputPolicy() {
        val state = MacPointerState()
        var old = false
        state.set(1, true, { old }, { old = it })
        state.set(2, false, { error("different native peer") }, { error("different native peer") })
        var replacement = true
        state.set(2, true, { replacement }, { replacement = it })
        state.set(2, false, { replacement }, { replacement = it })
        assertTrue(replacement)
        assertFails { state.set(0, true, { false }, {}) }
    }
}
