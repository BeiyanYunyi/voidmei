package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voidmei.config.EngineControlDimensions

internal val LocalEngineControlDimensions = staticCompositionLocalOf<EngineControlDimensions?> { null }

@Composable
internal fun Modifier.engineHorizontalDimensions(defaultHeight: Dp? = null): Modifier {
    val dimensions = LocalEngineControlDimensions.current
    return if (dimensions != null) widthIn(max = dimensions.lengthDp.dp).fillMaxWidth().height(dimensions.thicknessDp.dp)
    else fillMaxWidth().then(if (defaultHeight != null) Modifier.height(defaultHeight) else Modifier)
}
