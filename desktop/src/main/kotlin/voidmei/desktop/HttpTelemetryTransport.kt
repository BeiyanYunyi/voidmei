package voidmei.desktop

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import voidmei.telemetry.TelemetryTransport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun validateEndpoint(baseUrl: String): URI {
    val base = URI(baseUrl.trimEnd('/'))
    require(base.scheme in listOf("http", "https") && base.host != null &&
            base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null &&
            base.path in listOf("", "/") && (base.port == -1 || base.port in 1..65535)) {
        "请输入有效的 HTTP 服务器地址，例如 http://127.0.0.1:8111"
    }
    return base
}

class HttpTelemetryTransport(baseUrl: String) : TelemetryTransport, AutoCloseable {
    private val base = validateEndpoint(baseUrl)

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    override suspend fun get(path: String): String {
        val bytes = getBytes(path, if (path == "/map_obj.json") 8 * 1024 * 1024 else 1024 * 1024)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    internal suspend fun getBytes(path: String, limit: Int): ByteArray =
        withTimeoutOrNull(2000) { receiveBytes(path, limit) }
            ?: throw java.net.SocketTimeoutException("$path: 响应读取超时（2 秒）")

    private suspend fun receiveBytes(path: String, limit: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(2)).GET().build()
        require(limit in 1..16 * 1024 * 1024)
        val handler = HttpResponse.BodyHandler<ByteArray> { info ->
            val failure = when {
                info.statusCode() != 200 -> IllegalStateException("$path: HTTP ${info.statusCode()}")
                info.headers().firstValueAsLong("Content-Length").orElse(0) > limit -> IllegalStateException("$path: 响应超过 $limit 字节限制")
                else -> null
            }
            LimitedBodySubscriber(limit, failure)
        }
        val future = client.sendAsync(request, handler)
        continuation.invokeOnCancellation { future.cancel(true) }
        future.whenComplete { response, error ->
            if (error != null) continuation.resumeWithException(if (error is CompletionException) error.cause ?: error else error)
            else if (response.statusCode() != 200) continuation.resumeWithException(
                IllegalStateException("$path: HTTP ${response.statusCode()}"))
            else continuation.resume(response.body())
        }
    }

    override fun close() { client.shutdownNow() }
}

/** Bounds allocation while chunks arrive, even when Content-Length is absent or incorrect. */
private class LimitedBodySubscriber(private val limit: Int, private val initialFailure: Throwable?) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val bytes = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null
    override fun getBody(): CompletionStage<ByteArray> = result
    override fun onSubscribe(value: Flow.Subscription) {
        if (subscription != null) { value.cancel(); return }
        subscription = value
        if (initialFailure != null) { value.cancel(); result.completeExceptionally(initialFailure) }
        else value.request(1)
    }
    override fun onNext(items: List<ByteBuffer>) {
        if (result.isDone) return
        for (buffer in items) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription?.cancel()
                result.completeExceptionally(IllegalStateException("遥测响应超过 $limit 字节限制"))
                return
            }
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            bytes.write(chunk)
        }
        subscription?.request(1)
    }
    override fun onError(error: Throwable) { result.completeExceptionally(error) }
    override fun onComplete() { if (!result.isDone) result.complete(bytes.toByteArray()) }
}
