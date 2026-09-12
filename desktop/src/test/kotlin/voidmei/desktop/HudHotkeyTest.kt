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

    @Test fun bothActionsShareOneRegistrationAndTrackPressedKeysIndependently() {
        val backend = Backend()
        var hud = 0
        var model = 0
        val session = HudHotkey(backend, modelToggle = { model++ }) { hud++ }
        session.start()
        val m = event(key = NativeKeyEvent.VC_M)
        backend.listener.nativeKeyPressed(event())
        backend.listener.nativeKeyPressed(m)
        backend.listener.nativeKeyPressed(event())
        backend.listener.nativeKeyPressed(m)
        assertEquals(1, hud); assertEquals(1, model); assertEquals(1, backend.starts)
        backend.listener.nativeKeyReleased(m)
        backend.listener.nativeKeyPressed(m)
        assertEquals(1, hud); assertEquals(2, model)
        for (modifiers in listOf(0, NativeInputEvent.CTRL_MASK, chord or NativeInputEvent.ALT_MASK, chord or NativeInputEvent.META_MASK)) {
            backend.listener.nativeKeyReleased(m)
            backend.listener.nativeKeyPressed(event(modifiers, NativeKeyEvent.VC_M))
        }
        assertEquals(2, model)
        backend.listener.nativeKeyReleased(m)
        backend.listener.nativeKeyPressed(event(chord or NativeInputEvent.CAPS_LOCK_MASK, NativeKeyEvent.VC_M))
        assertEquals(3, model)
        session.close()
        backend.listener.nativeKeyReleased(m)
        backend.listener.nativeKeyPressed(m)
        assertEquals(3, model); assertEquals(1, backend.stops)
    }

    @Test fun customBindingUpdatesWithoutReregisteringAndCodesMatchNativeLibrary() {
        for (key in voidmei.config.ModelHotkeyKey.entries)
            assertEquals(NativeKeyEvent::class.java.getField("VC_${key.name}").getInt(null), key.nativeCode)
        val backend = Backend()
        var binding = voidmei.config.ModelHotkey.parse("P")
        var model = 0
        var hud = 0
        HudHotkey(backend, modelToggle = { model++ }, modelBinding = { binding }) { hud++ }.use { session ->
            session.start()
            backend.listener.nativeKeyPressed(event(0, NativeKeyEvent.VC_P))
            backend.listener.nativeKeyReleased(event(0, NativeKeyEvent.VC_P))
            binding = voidmei.config.ModelHotkey.parse("Alt+F1")
            backend.listener.nativeKeyPressed(event(0, NativeKeyEvent.VC_P))
            backend.listener.nativeKeyPressed(event(NativeInputEvent.ALT_MASK, NativeKeyEvent.VC_F1))
            backend.listener.nativeKeyPressed(event())
            assertEquals(2, model); assertEquals(1, hud); assertEquals(1, backend.starts)
        }
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
