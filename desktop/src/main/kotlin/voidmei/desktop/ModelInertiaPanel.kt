package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale
import voidmei.fm.ModelInertia

@Composable
internal fun ModelInertiaPanel(inertia: ModelInertia?) {
    Text("三轴转动惯量（模型原值）", style = MaterialTheme.typography.titleMedium)
    fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"
    Text("俯仰 P：${inertia?.pitch.shown()}")
    Text("滚转 R：${inertia?.roll.shown()}")
    Text("偏航 Y：${inertia?.yaw.shown()}")
    if (inertia == null) Text("模型未提供有效转动惯量向量")
    else Text("来源：${inertia.sourcePath}", style = MaterialTheme.typography.bodySmall)
    Text("沿用旧版轴顺序：向量第 3／1／2 个分量对应 P／R／Y。保留字段原值，字段未单独注明单位，不进行换算。", style = MaterialTheme.typography.bodySmall)
}
