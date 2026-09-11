package voidmei.telemetry

import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow

enum class HudMessageKind { EVENT, DAMAGE }
data class HudMessage(val kind: HudMessageKind, val id: Int, val text: String)
data class HudMessageState(
    val messages: List<HudMessage>, val error: String? = null,
    // Cursors outlive entries evicted from the bounded history, including across a pause.
    val lastEventId: Int = messages.filter { it.kind == HudMessageKind.EVENT }.maxOfOrNull { it.id } ?: 0,
    val lastDamageId: Int = messages.filter { it.kind == HudMessageKind.DAMAGE }.maxOfOrNull { it.id } ?: 0,
)

object HudMessageParser {
    fun parse(text: String): List<HudMessage> {
        require(text.length <= 1024 * 1024) { "消息响应过大" }
        val root = Json.parseToJsonElement(text).jsonObject
        return listOf("events" to HudMessageKind.EVENT, "damage" to HudMessageKind.DAMAGE).flatMap { (key, kind) ->
            val rows = root[key] as? JsonArray ?: error("消息响应缺少 $key 数组")
            require(rows.size <= 1000) { "$key 消息数量过多" }
            val seen = mutableSetOf<Int>()
            rows.map { row ->
                val obj = row as? JsonObject ?: error("无效消息对象")
                val id = (obj["id"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                require(id != null && id >= 0 && seen.add(id)) { "消息编号无效或重复" }
                val message = (obj["msg"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                require(message != null && message.length <= 8192) { "消息文本无效或过长" }
                HudMessage(kind, id, message)
            }.sortedBy { it.id }
        }
    }
}

/** Independent cursors; failures never advance them. Supply a saved state only when resuming the same flight. */
class HudMessagePoller(private val transport: TelemetryTransport, private val intervalMs: Long = 1000) {
    init { require(intervalMs in 100..10000) }
    fun states(initial: HudMessageState = HudMessageState(emptyList())) = flow {
        var eventId = initial.lastEventId
        var damageId = initial.lastDamageId
        var history = initial.messages.takeLast(200)
        emit(initial.copy(messages = history))
        while (currentCoroutineContext().isActive) {
            val state = try {
                val incoming = withTimeout(2500) {
                    HudMessageParser.parse(transport.get("/hudmsg?lastEvt=$eventId&lastDmg=$damageId"))
                }
                val new = incoming.filter { it.id > if (it.kind == HudMessageKind.EVENT) eventId else damageId }
                eventId = maxOf(eventId, incoming.filter { it.kind == HudMessageKind.EVENT }.maxOfOrNull { it.id } ?: 0)
                damageId = maxOf(damageId, incoming.filter { it.kind == HudMessageKind.DAMAGE }.maxOfOrNull { it.id } ?: 0)
                history = (history + new).takeLast(200)
                HudMessageState(history, lastEventId = eventId, lastDamageId = damageId)
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                HudMessageState(history, "消息请求超时", eventId, damageId)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { HudMessageState(history, e.message ?: "消息不可用", eventId, damageId) }
            emit(state)
            delay(intervalMs)
        }
    }
}
