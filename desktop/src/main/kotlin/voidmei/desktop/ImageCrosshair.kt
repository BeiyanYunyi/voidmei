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

@Composable
internal fun ImageCrosshair(path: String, sizeDp: Int, modifier: Modifier = Modifier, stretch: Boolean = false, right: Boolean = false) = key(path) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
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
                bitmap = null
                failed = false
                try { bitmap = withContext(Dispatchers.IO) { loadCrosshairImage(Path.of(path)) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed = true }
                previous = stamp
                first = false
            }
            delay(1000)
        }
    }
    BoxWithConstraints(modifier, contentAlignment = if (right) Alignment.CenterEnd else Alignment.Center) {
        bitmap?.let { Image(it, "自定义图片准星", Modifier.size(minOf(sizeDp.dp, maxWidth, maxHeight)).testTag("hud-image-crosshair"), contentScale = if (stretch) androidx.compose.ui.layout.ContentScale.FillBounds else androidx.compose.ui.layout.ContentScale.Fit) }
        if (failed) Text("准星图片不可用，请重新选择图片。")
    }
}

private data class CrosshairFileStamp(val size: Long, val modified: java.nio.file.attribute.FileTime, val identity: String?)
