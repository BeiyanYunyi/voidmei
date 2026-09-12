package voidmei.desktop

import com.sun.jna.*
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicReference

internal object MacAppKit {
    private interface ObjC : Library {
        fun sel_registerName(name: String): Pointer
        fun objc_getClass(name: String): Pointer?
    }
    private fun interface DispatchAction : Callback { fun invoke(context: Pointer?) }
    private interface SystemApi : Library {
        fun pthread_main_np(): Int
        fun dispatch_sync_f(queue: Pointer, context: Pointer?, action: DispatchAction)
    }
    private val objc by lazy { Native.load("objc", ObjC::class.java) }
    private val message by lazy { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }
    private val system by lazy { Native.load("System", SystemApi::class.java) }
    private val mainQueue by lazy { NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q") }
    private val getPolicy by lazy { objc.sel_registerName("ignoresMouseEvents") }
    private val setPolicy by lazy { objc.sel_registerName("setIgnoresMouseEvents:") }

    private fun objectMessage(receiver: Pointer?, selector: String): Pointer? =
        if (receiver == null) null else message.invokePointer(arrayOf(receiver, objc.sel_registerName(selector)))

    fun frontmostApplication(): MacForegroundApplication? {
        check(Platform.isMac()) { "AppKit foreground detection requires macOS" }
        val result = AtomicReference<MacForegroundApplication?>()
        onMainThread {
            val pool = checkNotNull(objectMessage(objc.objc_getClass("NSAutoreleasePool"), "new"))
            try {
                val workspace = objectMessage(objc.objc_getClass("NSWorkspace"), "sharedWorkspace")
                val application = objectMessage(workspace, "frontmostApplication")
                if (application != null) {
                    val pid = message.invokeInt(arrayOf(application, objc.sel_registerName("processIdentifier")))
                    val url = objectMessage(application, "executableURL")
                    val path = objectMessage(url, "path")
                    val utf8 = objectMessage(path, "UTF8String")
                    val executable = utf8?.getString(0, "UTF-8")
                    if (pid > 0 && !executable.isNullOrBlank())
                        result.set(MacForegroundApplication(pid, executable))
                }
            } finally {
                message.invokeVoid(arrayOf(pool, objc.sel_registerName("drain")))
            }
        }
        return result.get()
    }

    // Objective-C BOOL is one byte on both supported macOS architectures; do not use JNA's 32-bit BOOL.
    fun ignoresMouse(window: Pointer): Boolean = (message.invoke(java.lang.Byte.TYPE, arrayOf(window, getPolicy)) as Byte).toInt() != 0
    fun ignoreMouse(window: Pointer, value: Boolean) {
        message.invokeVoid(arrayOf(window, setPolicy, if (value) 1.toByte() else 0.toByte()))
    }

    fun onMainThread(action: () -> Unit) {
        if (system.pthread_main_np() != 0) { action(); return }
        val failure = AtomicReference<Throwable?>()
        val callback = DispatchAction { try { action() } catch (e: Throwable) { failure.set(e) } }
        // Synchronous completion keeps the callback and leased JDK resource alive; no AWT calls inside.
        try { system.dispatch_sync_f(mainQueue, null, callback) }
        finally { Reference.reachabilityFence(callback) }
        failure.get()?.let { throw it }
    }
}
