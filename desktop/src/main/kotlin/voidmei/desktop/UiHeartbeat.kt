package voidmei.desktop

import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Diagnostic only: at most one queued AWT callback, even while the event thread is stuck. */
internal class UiHeartbeat(private val post: (() -> Unit) -> Unit,
    private val now: () -> Long = System::nanoTime, private val report: (String) -> Unit = ::println) : AutoCloseable {
    private var queuedAt: Long? = null
    private var reportedStall = false
    private var closed = false

    @Synchronized fun tick() {
        if (closed) return
        val current = now()
        val pending = queuedAt
        if (pending != null) {
            val elapsed = (current - pending) / 1_000_000
            if (elapsed >= 2000 && !reportedStall) {
                reportedStall = true
                report("[VoidMei UI] stalled_ms=$elapsed")
            }
        } else {
            queuedAt = current
            post { completed(current) }
        }
    }

    @Synchronized private fun completed(start: Long) {
        if (closed || queuedAt != start) return
        report("[VoidMei UI] latency_ms=${(now() - start) / 1_000_000}")
        queuedAt = null
        reportedStall = false
    }

    @Synchronized override fun close() { closed = true; queuedAt = null }
}

internal fun startUiHeartbeat(): AutoCloseable {
    val heartbeat = UiHeartbeat(post = { EventQueue.invokeLater(it) })
    val timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "voidmei-ui-heartbeat").apply { isDaemon = true } }
    timer.scheduleAtFixedRate(heartbeat::tick, 5, 1, TimeUnit.SECONDS)
    return AutoCloseable { heartbeat.close(); timer.shutdownNow() }
}
