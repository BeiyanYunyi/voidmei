package voidmei.desktop

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.Path

/** Invoked from a desktop click handler; cancel preserves the currently entered path. */
internal fun chooseCsvFile(current: String): String? = csvFileDialog(current, FileDialog.LOAD)
internal fun chooseCsvExport(current: String): String? = csvFileDialog(current, FileDialog.SAVE)
internal fun chooseLegacySettingsFile(current: String): String? = csvFileDialog(current, FileDialog.LOAD, "旧版布局设置")
internal fun chooseVoiceArchive(current: String): String? = csvFileDialog(current, FileDialog.LOAD, "ZIP 语音包")

private fun csvFileDialog(current: String, mode: Int, title: String = "CSV 记录"): String? {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, title, mode)
        is Dialog -> FileDialog(owner, title, mode)
        else -> FileDialog(null as Frame?, title, mode)
    }
    try {
        val initial = runCatching { current.takeIf { it.isNotBlank() }?.let { Path.of(it).toAbsolutePath() } }.getOrNull()
        initial?.let {
            if (Files.isDirectory(it)) dialog.directory = it.toString()
            else {
                dialog.directory = it.parent?.toString()
                dialog.file = it.fileName?.toString()
            }
        }
        dialog.isMultipleMode = false
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory ?: "", file).toAbsolutePath().normalize().toString()
    } finally { dialog.dispose() }
}

internal fun chooseCrosshairImage(current: String): String? = csvFileDialog(current, FileDialog.LOAD, "选择准星图片")
