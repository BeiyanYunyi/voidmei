package voidmei.config

/** Portable names; codes are JNativeHook VC constants, checked against the desktop dependency. */
enum class ModelHotkeyKey(val nativeCode: Int) {
    A(30), B(48), C(46), D(32), E(18), F(33), G(34), H(35), I(23), J(36), K(37), L(38), M(50),
    N(49), O(24), P(25), Q(16), R(19), S(31), T(20), U(22), V(47), W(17), X(45), Y(21), Z(44),
    F1(59), F2(60), F3(61), F4(62), F5(63), F6(64), F7(65), F8(66), F9(67), F10(68), F11(87), F12(88)
}

data class ModelHotkey(val key: ModelHotkeyKey, val ctrl: Boolean, val shift: Boolean, val alt: Boolean) {
    val conflictsWithHud: Boolean get() = key == ModelHotkeyKey.H && ctrl && shift && !alt
    val encoded: String get() = buildList {
        if (ctrl) add("Ctrl")
        if (shift) add("Shift")
        if (alt) add("Alt")
        add(key.name)
    }.joinToString("+")
    companion object {
        fun parse(value: String): ModelHotkey {
            val parts = value.split('+')
            val key = ModelHotkeyKey.entries.singleOrNull { it.name == parts.last() }
                ?: throw IllegalArgumentException("模型热键需为 A–Z 或 F1–F12")
            val result = ModelHotkey(key, "Ctrl" in parts, "Shift" in parts, "Alt" in parts)
            require(result.encoded == value) { "模型热键格式无效" }
            require(!result.conflictsWithHud) { "模型热键不能与 Ctrl+Shift+H 冲突" }
            return result
        }
    }
}
