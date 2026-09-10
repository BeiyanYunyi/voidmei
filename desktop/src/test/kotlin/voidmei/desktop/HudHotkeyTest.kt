package voidmei.desktop

import com.github.kwhat.jnativehook.NativeInputEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlin.test.*

class HudHotkeyTest {
    private class Backend : HotkeyBackend {
        var starts = 0
        var stops = 0
        var fail = false
        lateinit var listener: NativeKeyListener
        override fun start(listener: NativeKeyListener) {
            starts++
            this.listener = listener
            if (fail) error("registration failed")
        }
        override fun stop(listener: NativeKeyListener) { stops++ }
    }
    private val chord = NativeInputEvent.CTRL_MASK or NativeInputEvent.SHIFT_MASK
    private fun event(modifiers: Int = chord, key: Int = NativeKeyEvent.VC_H) =
        NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, modifiers, 0, key, NativeKeyEvent.CHAR_UNDEFINED)

    @Test fun exactChordIgnoresRepeatAndLockModifiers() {
        val backend = Backend()
        var toggles = 0
        HudHotkey(backend) { toggles++ }.use { session ->
            session.start()
            backend.listener.nativeKeyPressed(event())
            backend.listener.nativeKeyPressed(event())
            assertEquals(1, toggles)
            backend.listener.nativeKeyReleased(event())
            backend.listener.nativeKeyPressed(event(chord or NativeInputEvent.CAPS_LOCK_MASK))
            assertEquals(2, toggles)
            for (modifiers in listOf(0, NativeInputEvent.CTRL_MASK, chord or NativeInputEvent.ALT_MASK,
                chord or NativeInputEvent.META_MASK)) {
                backend.listener.nativeKeyReleased(event())
                backend.listener.nativeKeyPressed(event(modifiers))
            }
            backend.listener.nativeKeyPressed(event(key = NativeKeyEvent.VC_J))
            assertEquals(2, toggles)
        }
    }

    @Test fun closeUnregistersOnceAndRejectsLateEvents() {
        val backend = Backend()
        var toggles = 0
        val session = HudHotkey(backend) { toggles++ }
        session.start()
        session.start()
        session.close()
        session.close()
        backend.listener.nativeKeyPressed(event())
        assertEquals(0, toggles)
        assertEquals(1, backend.starts)
        assertEquals(1, backend.stops)
        assertFailsWith<IllegalStateException> { session.start() }
    }

    @Test fun failedRegistrationNeverActivatesCallback() {
        val backend = Backend().apply { fail = true }
        var toggles = 0
        val session = HudHotkey(backend) { toggles++ }
        assertFailsWith<IllegalStateException> { session.start() }
        backend.listener.nativeKeyPressed(event())
        session.close()
        assertEquals(0, toggles)
        assertEquals(0, backend.stops)
    }

    @Test fun bundledNativeLibraryCanBeExtractedOutsideInstallDirectory() {
        val library = HotkeyLibraryLocator().libraries.next()
        try {
            assertTrue(library.isFile)
            assertTrue(library.length() > 0)
            assertTrue(library.parentFile.name.startsWith("voidmei-hotkey-"))
        } finally {
            library.delete()
            library.parentFile.delete()
        }
    }
}
