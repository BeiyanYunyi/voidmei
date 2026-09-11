package voidmei.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voidmei.telemetry.MapBounds

/** One decoded map per HUD session. Concurrent regions share the same validated download. */
internal class HudMapBackgroundCache(private val endpoint: String) {
    private val mutex = Mutex()
    private var cached: Pair<MapBounds, ImageBitmap>? = null

    suspend fun load(bounds: MapBounds): ImageBitmap = mutex.withLock {
        cached?.takeIf { it.first == bounds }?.let { return@withLock it.second }
        cached = null
        withContext(Dispatchers.IO) {
            HttpTelemetryTransport(endpoint).use { loadMapBackground(it, bounds).toComposeImageBitmap() }
        }.also { cached = bounds to it }
    }
}

internal val LocalHudMapBackgroundCache = staticCompositionLocalOf<HudMapBackgroundCache?> { null }
