package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.*

internal enum class GameFocus { GAME, OTHER, UNKNOWN }

internal interface ForegroundProcessSource {
    fun activeWindow(): Long?
    fun processId(window: Long): Int?
    fun imageName(processId: Int): String?
}

internal fun detectGameFocus(source: ForegroundProcessSource): GameFocus = try {
    val window = source.activeWindow()
    val pid = window?.let(source::processId)?.takeUnless { it == 0 || it == 4 }
    val path = pid?.let(source::imageName)
    if (window == null || pid == null || path.isNullOrBlank() ||
        source.activeWindow() != window || source.processId(window) != pid) GameFocus.UNKNOWN
    else if (path.substringAfterLast('\\').substringAfterLast('/').equals("aces.exe", ignoreCase = true))
        GameFocus.GAME else GameFocus.OTHER
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    GameFocus.UNKNOWN
} catch (_: LinkageError) {
    GameFocus.UNKNOWN
}

@Composable
internal fun rememberGameFocus(enabled: Boolean): State<GameFocus> {
    val result = remember(enabled) { mutableStateOf(GameFocus.UNKNOWN) }
    LaunchedEffect(enabled) {
        if (enabled && com.sun.jna.Platform.isWindows()) {
            while (isActive) {
                result.value = withContext(Dispatchers.IO) { detectGameFocus(WindowsForegroundProcess) }
                delay(200)
            }
        }
    }
    return result
}
