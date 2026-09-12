package voidmei.config

/** Portable names; codes are JNativeHook VC constants, checked against the desktop dependency. */
enum class ModelHotkeyKey(val nativeCode: Int, private val title: String? = null) {
    A(30), B(48), C(46), D(32), E(18), F(33), G(34), H(35), I(23), J(36), K(37), L(38), M(50),
    N(49), O(24), P(25), Q(16), R(19), S(31), T(20), U(22), V(47), W(17), X(45), Y(21), Z(44),
    F1(59), F2(60), F3(61), F4(62), F5(63), F6(64), F7(65), F8(66), F9(67), F10(68), F11(87), F12(88),
    F13(91), F14(92), F15(93), F16(99), F17(100), F18(101), F19(102), F20(103), F21(104), F22(105), F23(106), F24(107),
    DIGIT0(11), DIGIT1(2), DIGIT2(3), DIGIT3(4), DIGIT4(5), DIGIT5(6), DIGIT6(7), DIGIT7(8), DIGIT8(9), DIGIT9(10),
    ESCAPE(1, "Esc"), BACKSPACE(14, "退格 Backspace"), TAB(15, "Tab"), ENTER(28, "回车 Enter"), SPACE(57, "空格 Space"),
    INSERT(3666, "插入 Insert"), DELETE(3667, "删除 Delete"), HOME(3655, "Home"), END(3663, "End"),
    PAGE_UP(3657, "上页 Page Up"), PAGE_DOWN(3665, "下页 Page Down"),
    UP(57416, "上 ↑"), DOWN(57424, "下 ↓"), LEFT(57419, "左 ←"), RIGHT(57421, "右 →"),
    BACKQUOTE(41, "反引号 `"), MINUS(12, "减号 -"), EQUALS(13, "等号 ="),
    OPEN_BRACKET(26, "左方括号 ["), CLOSE_BRACKET(27, "右方括号 ]"), BACK_SLASH(43, "反斜杠"),
    SEMICOLON(39, "分号 ;"), QUOTE(40, "引号"), COMMA(51, "逗号 ,"), PERIOD(52, "句号 ."), SLASH(53, "斜杠 /"),
    PRINTSCREEN(3639, "Print Screen"), PAUSE(3653, "Pause"), CONTEXT_MENU(3677, "菜单键");

    val nativeName: String get() = name.removePrefix("DIGIT")
    val label: String get() = title ?: nativeName
    fun matches(query: String): Boolean = query.trim().let { it.isEmpty() || name.contains(it, true) || label.contains(it, true) }
}

data class ModelHotkey(val key: ModelHotkeyKey, val ctrl: Boolean, val shift: Boolean, val alt: Boolean) {
    val conflictsWithHud: Boolean get() = key == ModelHotkeyKey.H && ctrl && shift && !alt
    val display: String get() = encoded.substringBeforeLast('+', "").let { if (it.isEmpty()) key.label else "$it+${key.label}" }
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
                ?: throw IllegalArgumentException("模型热键不是已支持的按键")
            val result = ModelHotkey(key, "Ctrl" in parts, "Shift" in parts, "Alt" in parts)
            require(result.encoded == value) { "模型热键格式无效" }
            require(!result.conflictsWithHud) { "模型热键不能与 Ctrl+Shift+H 冲突" }
            return result
        }
    }
}
