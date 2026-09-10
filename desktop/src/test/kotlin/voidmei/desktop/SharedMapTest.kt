package voidmei.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import voidmei.telemetry.MapConnection
import kotlin.test.*

class SharedMapTest {
    @Test fun consumersShareOneSourceAndLastRemovalClearsReplay() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        var started = 0
        var stopped = 0
        val shared = flow<MapConnection> {
            started++
            try {
                emit(MapConnection.Unavailable("session $started"))
                awaitCancellation()
            } finally { stopped++ }
        }.shareMap(owner)
        suspend fun until(condition: () -> Boolean) = withTimeout(3000) {
            while (!condition()) delay(1)
        }
        try {
            yield()
            assertEquals(0, started)
            val first = launch { shared.collect() }
            until { shared.value == MapConnection.Unavailable("session 1") }
            val second = launch { shared.collect() }
            yield()
            assertEquals(1, started)
            first.cancelAndJoin()
            yield()
            assertEquals(0, stopped)
            second.cancelAndJoin()
            until { stopped == 1 && shared.value == MapConnection.Connecting }
            val third = launch { shared.collect() }
            until { shared.value == MapConnection.Unavailable("session 2") }
            assertEquals(2, started)
            owner.cancel()
            until { stopped == 2 }
            third.cancelAndJoin()
        } finally { owner.cancel() }
    }
}
