package voidmei.fm

sealed interface BlkEntry { val name: String }
data class BlkField(override val name: String, val type: String, val values: List<String>) : BlkEntry {
    fun number(): Double? = values.singleOrNull()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
data class BlkBlock(override val name: String, val entries: List<BlkEntry>) : BlkEntry {
    fun field(name: String): BlkField? = entries.filterIsInstance<BlkField>().lastOrNull { it.name.equals(name, true) }
    fun fields(prefix: String = ""): List<Pair<String, BlkField>> = entries.flatMap { entry ->
        val path = if (prefix.isEmpty()) entry.name else "$prefix.${entry.name}"
        when (entry) {
            is BlkField -> listOf(path to entry)
            is BlkBlock -> entry.fields(path)
        }
    }
}

/** Parser for unpacked Dagor typed BLK text. Retains duplicate fields and blocks in source order. */
object BlkParser {
    fun parse(text: String, checkActive: () -> Unit = {}): BlkBlock = Reader(text, checkActive).parse()

    private data class Token(val value: String, val quoted: Boolean = false)
    private class Reader(private val text: String, private val checkActive: () -> Unit) {
        private var work = 0
        private fun checkpoint() { if (work++ % 1024 == 0) checkActive() }
        private var position = 0
        private var cursor = 0
        private val tokens = mutableListOf<Token>()
        fun parse(): BlkBlock {
            checkActive()
            require(text.length <= 16 * 1024 * 1024) { "BLK exceeds 16 MiB text limit" }
            while (position < text.length) {
                checkpoint()
                require(tokens.size < 1_000_000) { "BLK token limit exceeded" }
                val c = text[position]
                when {
                    c == '\r' || c == ' ' || c == '\t' || c == '\uFEFF' -> position++
                    text.startsWith("//", position) -> { while (position < text.length && text[position] != '\n') { checkpoint(); position++ } }
                    text.startsWith("/*", position) -> {
                        position += 2
                        while (position < text.length && !text.startsWith("*/", position)) { checkpoint(); position++ }
                        require(position < text.length) { "Unterminated BLK comment" }
                        position += 2
                    }
                    c == '"' || c == '\'' -> {
                        position++
                        val value = StringBuilder()
                        while (position < text.length && text[position] != c) {
                            checkpoint()
                            val next = text[position++]
                            if (next == '~') {
                                require(position < text.length) { "Unterminated BLK escape" }
                                val escaped = text[position++]
                                value.append(when (escaped) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> escaped })
                            } else value.append(next)
                        }
                        require(position < text.length) { "Unterminated BLK string" }
                        position++
                        tokens += Token(value.toString(), true)
                    }
                    c in "{}:=;,\n[]" -> { tokens += Token(c.toString()); position++ }
                    else -> {
                        val start = position
                        while (position < text.length && !text[position].isWhitespace() && text[position] !in "{}:=;,[]\"'" &&
                            !text.startsWith("//", position) && !text.startsWith("/*", position)) { checkpoint(); position++ }
                        require(position > start) { "Invalid BLK character at $position" }
                        tokens += Token(text.substring(start, position))
                    }
                }
            }
            val result = block("", 0)
            require(result.entries.isNotEmpty()) { "Empty BLK document" }
            return result
        }

        private fun isToken(value: String, offset: Int = 0) = tokens.getOrNull(cursor + offset)?.let { !it.quoted && it.value == value } == true
        private fun next(): Token = tokens.getOrNull(cursor++) ?: error("Unexpected end of BLK")
        private fun expect(value: String) { require(isToken(value)) { "Expected '$value' at token $cursor" }; cursor++ }
        private fun block(name: String, depth: Int): BlkBlock {
            require(depth <= 64) { "BLK nesting exceeds 64" }
            val entries = mutableListOf<BlkEntry>()
            while (cursor < tokens.size) {
                checkpoint()
                if (isToken("\n") || isToken(";")) { cursor++; continue }
                if (isToken("}")) {
                    require(depth > 0) { "Unexpected closing brace" }
                    cursor++
                    return BlkBlock(name, entries)
                }
                val key = next()
                require(!key.value.startsWith("@")) { "BLK directives require preprocessing" }
                require(key.quoted || key.value.none { it in "{}:=,[]" }) { "Invalid BLK key" }
                while (isToken("\n")) cursor++
                if (isToken("{")) {
                    cursor++
                    entries += block(key.value, depth + 1)
                } else {
                    expect(":")
                    var type = next().value
                    if (isToken("[")) { cursor++; expect("]"); type += "[]" }
                    expect("=")
                    val values = mutableListOf<String>()
                    var brackets = 0
                    while (cursor < tokens.size) {
                        checkpoint()
                        if (brackets == 0 && (isToken("\n") || isToken(";") || isToken("}") || isToken(":", 1) || isToken("{", 1))) break
                        require(!isToken("{") && !isToken("}")) { "Unexpected brace in BLK value" }
                        if (isToken("[")) brackets++
                        if (isToken("]")) { brackets--; require(brackets >= 0) { "Unbalanced BLK vector" } }
                        val token = next()
                        if (token.quoted || token.value !in listOf(",", "\n")) values += token.value
                    }
                    require(values.isNotEmpty() && brackets == 0) { "Missing or unbalanced BLK value" }
                    entries += BlkField(key.value, type, values)
                }
            }
            require(depth == 0) { "Unterminated BLK block '$name'" }
            return BlkBlock(name, entries)
        }
    }
}
