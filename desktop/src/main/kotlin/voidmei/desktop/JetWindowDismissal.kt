package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** A one-shot dismissal belongs to the current enabled interval and model session. */
@Composable
internal fun rememberJetWindowDismissed(enabled: Boolean, live: Boolean, trigger: Boolean, sessionKey: Any?): Boolean {
    var dismissed by remember(enabled, sessionKey) { mutableStateOf(false) }
    val currentTrigger by rememberUpdatedState(trigger)
    LaunchedEffect(enabled, live, sessionKey) {
        if (enabled && live && !dismissed) {
            snapshotFlow { currentTrigger }.first { it }
            delay(10_000)
            dismissed = true
        }
    }
    return dismissed
}
