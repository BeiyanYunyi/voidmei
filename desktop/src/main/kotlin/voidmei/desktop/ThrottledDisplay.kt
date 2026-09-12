package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

/** Keep the newest display sample without slowing telemetry or queuing stale samples. */
@Composable
internal fun <T> throttledDisplay(value: T, intervalMs: Int, resetKey: Any?): T {
    if (intervalMs == 0) return value
    val latest by rememberUpdatedState(value)
    var displayed by remember(intervalMs, resetKey) { mutableStateOf(value) }
    LaunchedEffect(intervalMs, resetKey) {
        snapshotFlow { latest }.conflate().collect {
            delay(intervalMs.toLong())
            displayed = latest
        }
    }
    return displayed
}
