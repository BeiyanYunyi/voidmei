package voidmei.desktop

import voidmei.config.AppSettings

/** Recovery takes priority so even a launcher containing --hud can open settings safely. */
internal fun AppSettings.withStartupHudOptions(args: Array<String>): AppSettings = copy(
    hudEnabled = when {
        "--no-hud" in args -> false
        "--hud" in args -> true
        else -> hudEnabled
    },
    hudCompatibilityMode = hudCompatibilityMode || "--compatible-hud" in args,
)
