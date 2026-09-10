package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.*

internal enum class GameFocus { GAME, OTHER, UNKNOWN }

internal interface ForegroundProcessSource {
    fun activeWindow(): Long?
    fun processId(window: Long): Int?
    fun imageName(processId: Int): String?
}

internal fun detectGameFocus(source: ForegroundProcessSource, executableNames: Set<String> = setOf("aces.exe")): GameFocus = try {
    val window = source.activeWindow()
    val pid = window?.let(source::processId)?.takeUnless { it == 0 || it == 4 }
    val path = pid?.let(source::imageName)
    if (window == null || pid == null || path.isNullOrBlank() ||
        source.activeWindow() != window || source.processId(window) != pid) GameFocus.UNKNOWN
    else if (executableNames.any { path.substringAfterLast('\\').substringAfterLast('/').equals(it, ignoreCase = true) })
        GameFocus.GAME else GameFocus.OTHER
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    GameFocus.UNKNOWN
} catch (_: LinkageError) {
    GameFocus.UNKNOWN
}

internal fun supportsX11Focus(environment: Map<String, String> = System.getenv()): Boolean =
    !environment["DISPLAY"].isNullOrBlank() && environment["WAYLAND_DISPLAY"].isNullOrBlank() &&
        !environment["XDG_SESSION_TYPE"].equals("wayland", ignoreCase = true)

internal fun supportsGameFocus(): Boolean = com.sun.jna.Platform.isWindows() ||
    (com.sun.jna.Platform.isLinux() && supportsX11Focus())

internal fun currentGameFocus(): GameFocus = try {
    when {
        com.sun.jna.Platform.isWindows() -> detectGameFocus(WindowsForegroundProcess)
        com.sun.jna.Platform.isLinux() && supportsX11Focus() ->
            X11ForegroundProcess().use { detectGameFocus(it, setOf("aces", "aces.exe")) }
        else -> GameFocus.UNKNOWN
    }
} catch (e: CancellationException) { throw e }
catch (_: Exception) { GameFocus.UNKNOWN }
catch (_: LinkageError) { GameFocus.UNKNOWN }

@Composable
internal fun rememberGameFocus(enabled: Boolean): State<GameFocus> {
    val result = remember(enabled) { mutableStateOf(GameFocus.UNKNOWN) }
    LaunchedEffect(enabled) {
        if (enabled && supportsGameFocus()) {
            while (isActive) {
                result.value = withContext(Dispatchers.IO) { currentGameFocus() }
                delay(200)
            }
        }
    }
    return result
}
