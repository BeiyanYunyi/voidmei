package voidmei.desktop

import kotlin.test.*

class UiHeartbeatTest {
    @Test fun blockedUiHasBoundedQueueAndDisposalIgnoresLateCallbacks() {
        var time = 0L
        val queue = mutableListOf<() -> Unit>()
        val reports = mutableListOf<String>()
        val heartbeat = UiHeartbeat({ queue.add(it) }, { time }, { reports.add(it) })
        heartbeat.tick()
        repeat(10) { time += 1_000_000_000; heartbeat.tick() }
        assertEquals(1, queue.size)
        assertEquals(listOf("[VoidMei UI] stalled_ms=2000"), reports)
        queue.removeAt(0).invoke()
        assertEquals("[VoidMei UI] latency_ms=10000", reports.last())
        heartbeat.tick()
        heartbeat.close()
        queue.removeAt(0).invoke()
        heartbeat.tick()
        assertEquals(2, reports.size)
        assertTrue(queue.isEmpty())
    }
}
