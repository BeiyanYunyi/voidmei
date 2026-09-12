package voidmei.desktop

import com.sun.jna.*
import java.awt.Component
import java.awt.EventQueue
import java.awt.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/** OpenJDK 21 AWT peer bridge; the launcher exports only the three packages used here. */
internal class MacPointerRegion(private val window: Window) {
    private val state = MacPointerState()

    fun setClickThrough(enabled: Boolean) {
        check(EventQueue.isDispatchThread()) { "Window input changes require the AWT event thread" }
        check(Platform.isMac()) { "AppKit pointer policy requires macOS" }
        check(window.isDisplayable) { "HUD window has no native peer" }
        val accessorClass = Class.forName("sun.awt.AWTAccessor")
        val accessor = accessorClass.getMethod("getComponentAccessor").invoke(null)
        val peer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
            .getMethod("getPeer", Component::class.java).invoke(accessor, window)
        val platform = Class.forName("sun.lwawt.LWWindowPeer").getMethod("getPlatformWindow").invoke(peer)
        val resource = Class.forName("sun.lwawt.macosx.CFRetainedResource")
        val actionType = Class.forName("sun.lwawt.macosx.CFRetainedResource\$CFNativeAction")
        val applied = AtomicBoolean()
        val action = Proxy.newProxyInstance(actionType.classLoader, arrayOf(actionType)) { proxy, method, args ->
            when (method.name) {
                "run" -> {
                    val handle = args!![0] as Long
                    val pointer = Pointer(handle)
                    state.set(handle, enabled, { MacAppKit.ignoresMouse(pointer) }, { MacAppKit.ignoreMouse(pointer, it) })
                    applied.set(true)
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "VoidMei AppKit input action"
                else -> error("Unexpected native action: ${method.name}")
            }
        }
        // Acquire the JDK resource lease on AppKit's thread, never cache an unretained NSWindow pointer.
        MacAppKit.onMainThread {
            try { resource.getMethod("execute", actionType).invoke(platform, action) }
            catch (e: InvocationTargetException) { throw IllegalStateException("AppKit input update failed", e.targetException) }
        }
        check(applied.get()) { "HUD native window was disposed before the input update" }
    }
}
