package voidmei.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path

internal fun loadCrosshairImage(path: Path): ImageBitmap {
    require(Files.isRegularFile(path)) { "准星图片不是普通文件" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(8 * 1024 * 1024 + 1) }
    return decodeBoundedImage(bytes, "准星图片").toComposeImageBitmap()
}

internal data class CrosshairImageState(val bitmap: ImageBitmap? = null, val failed: Boolean = false)

@Composable
internal fun rememberCrosshairImage(path: String,
    loader: suspend (Path) -> ImageBitmap = { loadCrosshairImage(it) }): CrosshairImageState = key(path) {
    var state by remember { mutableStateOf(CrosshairImageState()) }
    LaunchedEffect(path) {
        var previous: CrosshairFileStamp? = null
        var first = true
        while (isActive) {
            val stamp = withContext(Dispatchers.IO) {
                runCatching {
                    val attributes = Files.readAttributes(Path.of(path), java.nio.file.attribute.BasicFileAttributes::class.java)
                    CrosshairFileStamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey()?.toString())
                }.getOrNull()
            }
            if (first || stamp != previous) {
                try {
                    val bitmap = withContext(Dispatchers.IO) { loader(Path.of(path)) }
                    state = CrosshairImageState(bitmap)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { state = CrosshairImageState(failed = true) }
                previous = stamp
                first = false
            }
            delay(1000)
        }
    }
    state
}

@Composable
internal fun ImageCrosshair(path: String, sizeDp: Int, modifier: Modifier = Modifier, stretch: Boolean = false,
    right: Boolean = false, shared: CrosshairImageState? = null) = key(path) {
    val state = shared ?: rememberCrosshairImage(path)
    BoxWithConstraints(modifier, contentAlignment = if (right) Alignment.CenterEnd else Alignment.Center) {
        state.bitmap?.let { Image(it, "自定义图片准星", Modifier.size(minOf(sizeDp.dp, maxWidth, maxHeight)).testTag("hud-image-crosshair"), contentScale = if (stretch) androidx.compose.ui.layout.ContentScale.FillBounds else androidx.compose.ui.layout.ContentScale.Fit) }
        if (state.failed) Text("准星图片不可用，请重新选择图片。")
    }
}

private data class CrosshairFileStamp(val size: Long, val modified: java.nio.file.attribute.FileTime, val identity: String?)
