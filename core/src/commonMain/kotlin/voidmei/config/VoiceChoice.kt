package voidmei.config

/** Null pack inherits the selected global pack; legacy imports use an explicit pack. */
data class VoiceChoice(val enabled: Boolean = true, val pack: String? = null) {
    init { require(pack == null || isVoicePackName(pack)) { "语音包名称必须为单个目录名" } }
}

fun isVoicePackName(name: String) = name.isNotBlank() && name !in setOf(".", "..") &&
    name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 }
