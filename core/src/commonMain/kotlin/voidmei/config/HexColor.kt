package voidmei.config

/** User-facing RGB or RGBA hex, converted to unsigned ARGB for rendering. */
fun parseHexColor(value: String): Long? {
    if (!Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)) return null
    val rgb = value.substring(1, 7).toLong(16)
    val alpha = if (value.length == 9) value.substring(7, 9).toLong(16) else 255L
    return (alpha shl 24) or rgb
}
