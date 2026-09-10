package voidmei.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowScope
import java.awt.MouseInfo

/** Follow screen coordinates because moving the window changes local pointer coordinates. */
@Composable
internal fun WindowScope.HudDraggableArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.pointerInput(window) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            val origin = window.location
            down.consume()
            do {
                val event = awaitPointerEvent()
                val cursor = MouseInfo.getPointerInfo()?.location
                if (cursor != null) window.setLocation(origin.x + cursor.x - start.x, origin.y + cursor.y - start.y)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }, content = content)
}
