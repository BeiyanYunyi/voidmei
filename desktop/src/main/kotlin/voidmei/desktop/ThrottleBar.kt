package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import voidmei.telemetry.ConnectionState
import voidmei.telemetry.HudField

/** 110% full scale matches the legacy throttle gauge; the numeric reading stays uncapped. */
@Composable
internal fun ThrottleBar(flight: ConnectionState.Flying) {
    ThrottleBar(HudField.ENGINE1_THROTTLE.value(flight), 1, "hud-throttle-bar")
}

@Composable
internal fun ThrottleBar(value: Double?, engineIndex: Int, tag: String) {
    val throttle = value?.takeIf { it.isFinite() && it >= 0 } ?: return
    LinearProgressIndicator(
        progress = { (throttle / 110).coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
            contentDescription = "$engineIndex 号油门，满刻度 110%"
            stateDescription = if (throttle > 100) "油门超过 100%" else "油门不超过 100%"
        },
        color = if (throttle > 100) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
}
