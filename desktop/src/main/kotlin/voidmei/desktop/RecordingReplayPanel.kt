package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.*
import voidmei.recording.*
import java.util.Locale
import kotlin.math.roundToInt

internal data class ReplaySeekRequest(val serial: Int, val sampleId: Long)

@Composable
internal fun RecordingReplayPanel(text: String, fields: List<Pair<String, String>>, seek: ReplaySeekRequest? = null, window: LongRange? = null, onFrameChanged: (RecordedFrame?) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("原始帧同步回看") }
    if (!expanded) return
    val fieldIds = fields.map { it.first }.distinct()
    val frameCallback by rememberUpdatedState(onFrameChanged)
    DisposableEffect(text, fieldIds) { frameCallback(null); onDispose { frameCallback(null) } }
    var replay by remember(text, fieldIds) { mutableStateOf<RecordedReplay?>(null) }
    var error by remember(text, fieldIds) { mutableStateOf<String?>(null) }
    var index by remember(text) { mutableStateOf(0) }
    var playbackTime by remember(text) { mutableStateOf(0L) }
    var playing by remember(text) { mutableStateOf(false) }
    var restrict by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0) }
    var handledSeek by remember(text) { mutableStateOf<ReplaySeekRequest?>(null) }
    LaunchedEffect(text, fieldIds) {
        try { replay = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            RecordedReplay(text, fieldIds) { context.ensureActive() }
        } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    val bounds = replay?.let { data ->
        if (restrict && window != null) data.indicesWithin(window.first, window.last) else 0 until data.size
    }
    LaunchedEffect(replay, bounds) {
        playing = false
        if (bounds != null) {
            index = index.coerceIn(bounds)
            playbackTime = replay!!.timeAt(index)
        } else frameCallback(null)
    }
    LaunchedEffect(replay, seek) {
        val request = seek
        val data = replay
        if (request != null && data != null && request != handledSeek) {
            handledSeek = request
            playing = false
            val target = data.indexOfSample(request.sampleId)
            if (target != null && (bounds == null || target !in bounds)) error = "指定采样不在回看区间"
            else if (target == null) error = "记录不包含指定采样 ${request.sampleId}"
            else { index = target; playbackTime = data.timeAt(target); error = null }
        }
    }
    LaunchedEffect(playing, speed, replay, bounds, looping) {
        val data = replay
        if (playing && data != null && bounds != null) {
            val startTime = data.timeAt(bounds.first)
            val endTime = data.timeAt(bounds.last)
            if (endTime == startTime) { playing = false; return@LaunchedEffect }
            val playbackSpeed = speed
            val repeatPlayback = looping
            val originTime = playbackTime.coerceIn(startTime, endTime)
            val originClock = withFrameNanos { it }
            while (isActive) {
                val clock = withFrameNanos { it }
                // UI changes can arrive before Compose cancels this effect; never commit an obsolete tick.
                if (!playing || speed != playbackSpeed || looping != repeatPlayback) break
                val time = ReplayTime.position(startTime, endTime, originTime, (clock - originClock) / 1_000_000.0, playbackSpeed, repeatPlayback)
                playbackTime = time
                index = data.indexAt(time).coerceIn(bounds)
                if (!repeatPlayback && time >= endTime) { playing = false; break }
            }
        }
    }
    error?.let { Text("回看失败：$it", color = MaterialTheme.colorScheme.error) }
    val data = replay ?: run { if (error == null) Text("正在建立原始帧索引…"); return }
    if (bounds == null) {
        Text("当前区间没有原始帧")
        TextButton(onClick = { restrict = false; error = null }) { Text("回看完整记录") }
        return
    }
    val currentIndex = index.coerceIn(bounds)
    val frame = remember(data, currentIndex) { data.frame(currentIndex) }
    LaunchedEffect(frame) { frameCallback(frame) }
    Text("${if (restrict && window != null) "当前区间原始记录" else "整段原始记录"} · 第 ${currentIndex + 1}/${data.size} 帧 · 采样 ${frame.sampleId} · ${String.format(Locale.ROOT, "%.3f", frame.elapsedMs / 1000.0)} s",
        Modifier.testTag("replay-frame"))
    Text("播放位置 ${String.format(Locale.ROOT, "%.3f", playbackTime / 1000.0)} s", Modifier.testTag("replay-playhead"))
    Text("采样间隔内保留上一帧及其实际时间，不插值；暂停和变速保持当前播放位置。", style = MaterialTheme.typography.bodySmall)
    fun selectFrame(target: Int) { playing = false; index = target; playbackTime = data.timeAt(target) }
    Text("所有读数来自同一采样帧；空值保持未知，播放遵循记录时间间隔。可选择仅回看当前曲线区间。", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(restrict, { restrict = it }, enabled = window != null,
            modifier = Modifier.semantics { contentDescription = "仅回看曲线区间" })
        Text("仅回看曲线区间")
        Checkbox(looping, { looping = it }, enabled = data.timeAt(bounds.last) > data.timeAt(bounds.first),
            modifier = Modifier.semantics { contentDescription = "循环回看" })
        Text("循环回看")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = data.timeAt(bounds.last) > data.timeAt(bounds.first), onClick = {
            if (!playing && playbackTime >= data.timeAt(bounds.last)) {
                index = bounds.first
                playbackTime = data.timeAt(bounds.first)
            }
            playing = !playing
        }) { Text(if (playing) "暂停回看" else "播放回看") }
        TextButton(enabled = currentIndex > bounds.first, onClick = { selectFrame(currentIndex - 1) }) { Text("上一原始帧") }
        TextButton(enabled = currentIndex < bounds.last, onClick = { selectFrame(currentIndex + 1) }) { Text("下一原始帧") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(.5, 1.0, 2.0, 4.0).forEach { value ->
            FilterChip(speed == value, { speed = value }, label = { Text("${value}×") })
        }
    }
    if (bounds.first < bounds.last) Slider(currentIndex.toFloat(), { selectFrame(it.roundToInt().coerceIn(bounds)) },
        valueRange = bounds.first.toFloat()..bounds.last.toFloat(), modifier = Modifier.testTag("replay-slider"))
    fields.forEach { (key, label) ->
        val value = frame.values[key]
        Text("$label：${value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "未知"}")
    }
}
