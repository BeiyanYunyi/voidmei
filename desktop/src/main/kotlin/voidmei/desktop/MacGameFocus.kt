package voidmei.desktop

import kotlinx.coroutines.CancellationException

internal data class MacForegroundApplication(val pid: Int, val executable: String)

/** A switch during the query must not be mistaken for a stable non-game foreground app. */
internal fun detectMacGameFocus(read: () -> MacForegroundApplication? = MacAppKit::frontmostApplication): GameFocus = try {
    val first = read()
    if (first == null || first.pid <= 0 || first.executable.isBlank() || read() != first) GameFocus.UNKNOWN
    else if (first.executable.substringAfterLast('/').equals("aces", ignoreCase = true)) GameFocus.GAME
    else GameFocus.OTHER
} catch (e: CancellationException) { throw e }
catch (_: Exception) { GameFocus.UNKNOWN }
catch (_: LinkageError) { GameFocus.UNKNOWN }
