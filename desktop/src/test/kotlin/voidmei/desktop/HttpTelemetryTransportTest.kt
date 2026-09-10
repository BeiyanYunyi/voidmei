package voidmei.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import voidmei.telemetry.*
import kotlin.test.*

class HttpTelemetryTransportTest {
    @Test fun stalledBodyTimesOutWithoutCancellingCallerAndAllowsAnotherRequest(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val finish = java.util.concurrent.CountDownLatch(1)
        server.createContext("/stall") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write('{'.code)
                exchange.responseBody.flush()
                finish.await(10, java.util.concurrent.TimeUnit.SECONDS)
            } finally { exchange.close() }
        }
        server.createContext("/ready") { exchange ->
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            HttpTelemetryTransport("http://127.0.0.1:${server.address.port}").use { transport ->
                withTimeout(4000) {
                    assertFailsWith<java.io.IOException> { transport.get("/stall") }
                }
                assertTrue(currentCoroutineContext().isActive)
                finish.countDown()
                assertEquals("{}", withTimeout(4000) { transport.get("/ready") })
            }
        } finally { finish.countDown(); server.stop(0) }
    }

    @Test fun rejectsOversizedFixedAndChunkedBodiesAndInvalidUtf8(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val payload = ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() }
        listOf("/fixed", "/chunked").forEach { path ->
            server.createContext(path) { exchange ->
                try {
                    exchange.sendResponseHeaders(200, if (path == "/fixed") payload.size.toLong() else 0)
                    exchange.responseBody.use { it.write(payload) }
                } catch (_: java.io.IOException) { /* Expected when the bounded reader cancels. */ }
                finally { exchange.close() }
            }
        }
        server.createContext("/invalid") { exchange ->
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write(byteArrayOf(0xC3.toByte(), 0x28)) }
        }
        server.start()
        try {
            HttpTelemetryTransport("http://127.0.0.1:${server.address.port}").use { transport ->
                for (path in listOf("/fixed", "/chunked")) {
                    val error = assertFailsWith<IllegalStateException> { transport.get(path) }
                    assertTrue(error.message.orEmpty().contains("限制"))
                }
                assertFails { transport.get("/invalid") }
            }
        } finally { server.stop(0) }
    }

    @Test fun cancellingSlowBodyReturnsWithoutWaitingForServer(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val started = CompletableDeferred<Unit>()
        val finish = java.util.concurrent.CountDownLatch(1)
        server.createContext("/slow") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write('{'.code)
                exchange.responseBody.flush()
                started.complete(Unit)
                finish.await(5, java.util.concurrent.TimeUnit.SECONDS)
            } finally { exchange.close() }
        }
        server.start()
        try {
            HttpTelemetryTransport("http://127.0.0.1:${server.address.port}").use { transport ->
                val request = launch { transport.get("/slow") }
                withTimeout(2000) { started.await() }
                withTimeout(1000) { request.cancelAndJoin() }
                assertTrue(request.isCancelled)
            }
        } finally { finish.countDown(); server.stop(0) }
    }

    @Test fun readsChunkedUtf8ResponsesAndRejectsHttpErrors(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/state") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("""{"valid":true,"IAS, km/h":474}""".toByteArray())
            }
        }
        server.createContext("/indicators") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write("""{"valid":true,"type":"测试机"}""".toByteArray()) }
        }
        server.start()
        try {
            HttpTelemetryTransport("http://127.0.0.1:${server.address.port}").use { transport ->
                val state = TelemetryPoller(transport).states().filterIsInstance<ConnectionState.Flying>().first()
                assertEquals(474.0, state.telemetry.iasKmh)
                assertEquals("测试机", state.telemetry.aircraft)
                assertFailsWith<IllegalStateException> { transport.get("/missing") }
            }
        } finally { server.stop(0) }
    }

    @Test fun validatesEndpoint() {
        listOf("file:///tmp/state", "http://localhost:8111/path", "http://user:pass@localhost:8111", "http://localhost?x=y")
            .forEach { address -> assertFailsWith<IllegalArgumentException> { HttpTelemetryTransport(address) } }
    }
}
