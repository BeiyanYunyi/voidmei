package voidmei.desktop

import androidx.compose.runtime.*

/** Request attention on failure changes, never on routine sample counts or callback recomposition. */
@Composable
internal fun RecordingFailureEffect(state: RecordingState, operationError: String?, onFailure: () -> Unit) {
    val callback by rememberUpdatedState(onFailure)
    val failure = state as? RecordingState.Failed
    LaunchedEffect(failure, operationError) {
        if (failure != null || operationError != null) callback()
    }
}
