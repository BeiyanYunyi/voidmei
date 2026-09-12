package voidmei.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeInputEvent
import com.github.kwhat.jnativehook.NativeLibraryLocator
import com.github.kwhat.jnativehook.NativeSystem
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

internal interface HotkeyBackend {
    fun start(listener: NativeKeyListener)
    fun stop(listener: NativeKeyListener)
}

/** One session per enabled interval. Call start/close on the same IO coroutine. */
internal class HudHotkey(
    private val backend: HotkeyBackend = NativeHotkeyBackend(),
    private val modelToggle: (() -> Unit)? = null,
    private val toggle: () -> Unit,
) : AutoCloseable {
    @Volatile private var active = false
    private var started = false
    private var closed = false
    private val pressed = mutableSetOf<Int>()
    internal val listener = object : NativeKeyListener {
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            if (!active || event.keyCode !in setOf(NativeKeyEvent.VC_H, NativeKeyEvent.VC_M)) return
            if (!pressed.add(event.keyCode)) return
            val modifiers = event.modifiers
            if (modifiers and NativeInputEvent.CTRL_MASK != 0 &&
                modifiers and NativeInputEvent.SHIFT_MASK != 0 &&
                modifiers and (NativeInputEvent.ALT_MASK or NativeInputEvent.META_MASK) == 0) {
                if (event.keyCode == NativeKeyEvent.VC_H) toggle() else modelToggle?.invoke()
            }
        }
        override fun nativeKeyReleased(event: NativeKeyEvent) {
            pressed.remove(event.keyCode)
        }
    }

    fun start() {
        check(!closed) { "Hotkey session is closed" }
        if (started) return
        // The backend must roll back a failed registration before throwing.
        backend.start(listener)
        started = true
        active = true
    }

    override fun close() {
        active = false // Ignore events already queued by the native dispatcher.
        closed = true
        if (started) {
            backend.stop(listener)
            started = false
        }
    }
}

internal class NativeHotkeyBackend : HotkeyBackend {
    override fun start(listener: NativeKeyListener) {
        check(!System.getProperty("os.name").startsWith("Linux") ||
            (System.getenv("WAYLAND_DISPLAY").isNullOrBlank() &&
                !System.getenv("XDG_SESSION_TYPE").equals("wayland", ignoreCase = true))) {
            "当前 Wayland 会话尚不支持全局热键，请使用窗口内的 HUD 按钮"
        }
        System.setProperty("jnativehook.lib.locator", HotkeyLibraryLocator::class.java.name)
        Logger.getLogger("com.github.kwhat.jnativehook").level = Level.OFF
        check(!GlobalScreen.isNativeHookRegistered()) { "全局热键监听器已被占用" }
        // JNativeHook 2.2.2 otherwise reuses its terminated dispatcher after re-enabling.
        GlobalScreen.setEventDispatcher(null)
        try {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
        } catch (failure: Throwable) {
            try { stop(listener) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override fun stop(listener: NativeKeyListener) {
        GlobalScreen.removeNativeKeyListener(listener)
        try { GlobalScreen.unregisterNativeHook() }
        finally { GlobalScreen.setEventDispatcher(null) }
    }
}

/** The default locator writes beside its JAR, which is read-only in installed applications. */
class HotkeyLibraryLocator : NativeLibraryLocator {
    override fun getLibraries(): Iterator<File> {
        val family = NativeSystem.getFamily().toString().lowercase(Locale.ROOT)
        val arch = NativeSystem.getArchitecture().toString().lowercase(Locale.ROOT)
        val name = System.mapLibraryName("JNativeHook").replace(".jnilib", ".dylib")
        val resource = "/com/github/kwhat/jnativehook/lib/$family/$arch/$name"
        val input = checkNotNull(javaClass.getResourceAsStream(resource)) { "缺少此平台的热键库：$family/$arch" }
        input.use {
            val directory = Files.createTempDirectory("voidmei-hotkey-")
            directory.toFile().deleteOnExit()
            val library = directory.resolve(name)
            library.toFile().deleteOnExit()
            Files.copy(it, library)
            return listOf(library.toFile()).iterator()
        }
    }
}
