package voidmei.desktop

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/** Select the same 25 m sample as the keyboard-accessible slider. */
@Composable
internal fun Modifier.powerAltitudePicker(onSelect: (Float) -> Unit): Modifier {
    return chartFractionPicker { fraction ->
        val altitude = fraction * 10000f
        onSelect((altitude / POWER_CURVE_STEP_M).roundToInt() * POWER_CURVE_STEP_M.toFloat())
    }
}

@Composable
internal fun Modifier.chartFractionPicker(onSelect: (Float) -> Unit): Modifier {
    val select by rememberUpdatedState(onSelect)
    return pointerInput(Unit) {
        detectTapGestures { position ->
            if (size.width > 0) {
                select((position.x / size.width).coerceIn(0f, 1f))
            }
        }
    }
}
