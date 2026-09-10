package voidmei.desktop

import kotlin.test.*

class WindowsPointerStyleTest {
    @Test fun restorePreservesLayeringAndOtherStyleChanges() {
        var flags = WindowsPointerStyle.LAYERED or 0x8 // TOPMOST
        val style = WindowsPointerStyle({ flags }, { flags = it })
        style.set(true)
        assertEquals(WindowsPointerStyle.LAYERED or 0x28, flags)
        style.set(true) // Repeated enables must not overwrite the original state.
        flags = flags or 0x08000000 // NOACTIVATE added independently.
        style.set(false)
        assertEquals(WindowsPointerStyle.LAYERED or 0x08000008, flags)
        style.set(true)
        style.set(false)
        assertEquals(WindowsPointerStyle.LAYERED or 0x08000008, flags)
    }

    @Test fun failedWriteThatAlreadyChangedTheStyleCanBeRestored() {
        var flags = WindowsPointerStyle.LAYERED
        var fail = true
        val style = WindowsPointerStyle({ flags }, {
            flags = it
            if (fail) error("frame refresh failed")
        })
        assertFails { style.set(true) }
        assertEquals(WindowsPointerStyle.LAYERED or WindowsPointerStyle.TRANSPARENT, flags)
        fail = false
        style.set(false)
        assertEquals(WindowsPointerStyle.LAYERED, flags)
    }

    @Test fun ordinaryWindowIsNotConvertedToLayeredBehindTheRenderer() {
        var writes = 0
        val style = WindowsPointerStyle({ 0x8 }, { writes++ })
        assertFails { style.set(true) }
        style.set(false)
        assertEquals(0, writes)
    }

    @Test fun ignoredNativeChangesAreReported() {
        val style = WindowsPointerStyle({ WindowsPointerStyle.LAYERED }, {})
        assertFails { style.set(true) }
        style.set(false)
    }

    @Test fun preexistingTransparentStyleIsPreserved() {
        var flags = WindowsPointerStyle.LAYERED or WindowsPointerStyle.TRANSPARENT
        val style = WindowsPointerStyle({ flags }, { flags = it })
        style.set(true)
        style.set(false)
        assertEquals(WindowsPointerStyle.LAYERED or WindowsPointerStyle.TRANSPARENT, flags)
    }
}
