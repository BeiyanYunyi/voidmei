package voidmei.fm

import kotlinx.serialization.json.Json

/** Supports typed BLK and JSON exports; duplicate object keys remain duplicate entries. */
object FlightModelDocument {
    fun parse(text: String, checkActive: () -> Unit = {}): BlkBlock {
        checkActive()
        val source = text.removePrefix("\uFEFF")
        return if (source.trimStart().startsWith('{')) JsonReader(source, checkActive).read() else BlkParser.parse(source, checkActive)
    }

    private sealed interface Value {
        data class Object(val entries: List<Pair<String, Value>>) : Value
        data class Scalar(val type: String, val value: String) : Value
        data class Array(val items: List<Value>, val start: Int, val end: Int) : Value
    }

    private class JsonReader(private val text: String, private val checkActive: () -> Unit) {
        private var work = 0
        private fun checkpoint() { if (work++ % 1024 == 0) checkActive() }
        private var position = 0
        private var nodes = 0
        private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
        fun read(): BlkBlock {
            require(text.length <= 16 * 1024 * 1024) { "JSON FM exceeds 16 MiB text limit" }
            val value = value(0)
            whitespace()
            require(position == text.length) { "Trailing JSON content" }
            require(value is Value.Object && value.entries.isNotEmpty()) { "Expected nonempty JSON FM object" }
            return block("", value)
        }
        private fun whitespace() { while (position < text.length && text[position] in " \n\r\t") { checkpoint(); position++ } }
        private fun take(char: Char): Boolean {
            whitespace()
            return if (position < text.length && text[position] == char) { position++; true } else false
        }
        private fun expect(char: Char) { require(take(char)) { "Expected '$char' at $position" } }
        private fun string(): String {
            whitespace()
            val start = position
            require(position < text.length && text[position++] == '"') { "Expected JSON string" }
            while (position < text.length) {
                checkpoint()
                when (text[position++]) {
                    '\\' -> { require(position < text.length); position++ }
                    '"' -> return Json.decodeFromString<String>(text.substring(start, position))
                }
            }
            error("Unterminated JSON string")
        }
        private fun value(depth: Int): Value {
            checkpoint()
            require(depth <= 64 && ++nodes <= 1_000_000) { "JSON FM structure limit exceeded" }
            whitespace()
            require(position < text.length) { "Missing JSON value" }
            return when (text[position]) {
                '{' -> {
                    position++
                    val entries = mutableListOf<Pair<String, Value>>()
                    if (!take('}')) {
                        do { val key = string(); expect(':'); entries += key to value(depth + 1) } while (take(','))
                        expect('}')
                    }
                    Value.Object(entries)
                }
                '[' -> {
                    val start = position++
                    val items = mutableListOf<Value>()
                    if (!take(']')) {
                        do { items += value(depth + 1) } while (take(','))
                        expect(']')
                    }
                    Value.Array(items, start, position)
                }
                '"' -> Value.Scalar("t", string())
                't', 'f', 'n' -> {
                    val literal = when (text[position]) { 't' -> "true"; 'f' -> "false"; else -> "null" }
                    require(text.startsWith(literal, position)) { "Invalid JSON literal" }
                    position += literal.length
                    Value.Scalar(if (literal == "null") "null" else "b", literal)
                }
                else -> {
                    val token = number.find(text, position)?.takeIf { it.range.first == position }?.value
                        ?: error("Invalid JSON number at $position")
                    require(token.toDoubleOrNull()?.isFinite() == true) { "Non-finite JSON number" }
                    position += token.length
                    Value.Scalar(if (token.any { it in ".eE" }) "r" else "i64", token)
                }
            }
        }
        private fun block(name: String, value: Value.Object): BlkBlock = BlkBlock(name, value.entries.flatMap { (key, item) ->
            checkpoint()
            when (item) {
                is Value.Object -> listOf(block(key, item))
                is Value.Scalar -> listOf(BlkField(key, item.type, listOf(item.value)))
                is Value.Array -> {
                    val scalars = item.items.filterIsInstance<Value.Scalar>()
                    when {
                        item.items.isNotEmpty() && item.items.all { it is Value.Object } -> item.items.map { block(key, it as Value.Object) }
                        scalars.size == item.items.size && scalars.size in 2..4 && scalars.all { it.type in listOf("r", "i64") } ->
                            listOf(BlkField(key, "p${scalars.size}", scalars.map { it.value }))
                        else -> listOf(BlkField(key, "json", listOf(text.substring(item.start, item.end))))
                    }
                }
            }
        })
    }
}
