package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun RecordingState.Failed.description(): String = reason + (files?.let {
    "\n飞行 CSV 目标：${it.flight}\n发动机 CSV 目标：${it.engines}\n目标文件可能未创建或内容不完整。"
} ?: "")

internal suspend fun stopRecordingForExit(recorder: FlightRecorder): String? = try {
    recorder.stop()
    (recorder.state.value as? RecordingState.Failed)?.description()
} catch (e: CancellationException) {
    currentCoroutineContext().ensureActive()
    // A stopped recorder can cancel its command channel without cancelling this caller.
    (recorder.state.value as? RecordingState.Failed)?.description()
        ?: (e.message ?: "录制工作协程已停止，无法确认记录完整性")
}
catch (e: Exception) { e.message ?: "无法正常停止记录" }

@Composable
internal fun RecordingExitDialog(reason: String, onReturn: () -> Unit, onExit: () -> Unit,
    copyText: ((String) -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onReturn, title = { Text("记录未正常完成") },
        text = {
            Column {
                SelectionContainer(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).testTag("recording-exit-details")) {
                    Text("$reason\n\nCSV 数据可能不完整。退出不会修复记录；返回后可检查保存目录及记录状态。")
                }
                TextButton(onClick = { if (copyText != null) copyText(reason) else clipboard.setText(AnnotatedString(reason)) }) {
                    Text("复制错误信息")
                }
            }
        },
        confirmButton = { TextButton(onClick = onExit) { Text("仍然退出") } },
        dismissButton = { TextButton(onClick = onReturn) { Text("返回检查") } })
}
