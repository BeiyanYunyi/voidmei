package voidmei.config

/** Replace only the port, preserving the current host and any endpoint suffix. */
internal fun replaceTelemetryPort(endpoint: String, port: Int): String {
    require(port in 1..65535)
    val parts = Regex("^(https?://)(\\[[^\\]]+\\]|[^/:?#@]+)(?::[0-9]+)?([/?#].*)?$", RegexOption.IGNORE_CASE)
        .matchEntire(endpoint)
    requireNotNull(parts) { "当前遥测地址无效，无法导入端口" }
    return parts.groupValues[1] + parts.groupValues[2] + ":$port" + parts.groupValues[3]
}
