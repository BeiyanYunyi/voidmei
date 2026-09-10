package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.config.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

internal fun readLegacySettings(path: Path, resourceRoot: Path? = null): LegacySettings {
    require(Files.isRegularFile(path)) { "请选择普通旧版设置文件" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(LegacySettingsReader.MAX_BYTES + 1) }
    require(bytes.size <= LegacySettingsReader.MAX_BYTES) { "旧配置超过 1 MiB" }
    return resolveLegacyCrosshair(LegacySettingsReader.read(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()), path, resourceRoot)
}

@Composable
fun LegacySettingsPanel(chooseFile: (String) -> String? = ::chooseLegacySettingsFile, onApply: (LegacySettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("导入旧版设置") }
    if (!expanded) return
    var path by remember { mutableStateOf("ui_layout.user.cfg") }
    var resourceRoot by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<LegacySettings?>(null) }
    var source by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("迁移自动记录与托盘启动偏好、刷新间隔、已支持的 HUD 字段、姿态、机械化、准星、表格配色、迎角预警阈值及语音设置；具体变更见预览。旧窗口布局、字体、屏幕位置和透明度尚不迁移，其余 Kotlin 设置与原文件保持不变。")
        OutlinedTextField(path, { path = it; preview = null; status = null }, Modifier.fillMaxWidth(),
            label = { Text("旧版布局文件路径（UTF-8）") }, enabled = !busy, singleLine = true)
        TextButton(enabled = !busy, onClick = {
            chooseFile(path)?.let { selected -> path = selected; preview = null; status = null }
        }) { Text("选择旧版设置文件") }
        OutlinedTextField(resourceRoot, { resourceRoot = it; preview = null; status = null }, Modifier.fillMaxWidth(),
            label = { Text("旧程序资源目录（留空使用配置所在目录）") }, enabled = !busy, singleLine = true)
        Text("图片查找位置：所选目录/image/gunsight/旧图片名.png。")
        Button(enabled = !busy && path.isNotBlank(), onClick = {
            val selected = path
            val selectedRoot = resourceRoot.takeIf { it.isNotBlank() }
            preview = null
            status = null
            busy = true
            scope.launch {
                try {
                    preview = withContext(Dispatchers.IO) { readLegacySettings(Path.of(selected), selectedRoot?.let { Path.of(it) }) }
                    source = selected
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "读取失败：${e.message}" }
                finally { busy = false }
            }
        }) { Text("预览旧设置") }
        preview?.let { imported ->
            Text("来源：$source")
            var showUnmigrated by remember(imported) { mutableStateOf(false) }
            if (imported.unmigrated.isNotEmpty()) {
                TextButton(onClick = { showUnmigrated = !showUnmigrated }) {
                    Text("未迁移 ${imported.unmigrated.size} 项：${if (showUnmigrated) "收起" else "查看"}")
                }
                if (showUnmigrated) {
                    imported.unmigrated.take(100).forEach { item ->
                        Text("${item.label.take(120)}（${item.target.take(120)}）：尚不支持迁移")
                    }
                    if (imported.unmigrated.size > 100) Text("仅显示前 100 项；其余请查看原文件。")
                }
            }
            if (!imported.hasChanges) Text("此文件没有可应用的设置。")
            imported.hudAltitudeMode?.let { Text("HUD 高度来源：${it.label}（雷达单位未判定时使用海拔）") }
            imported.hudNumberFont?.let { Text("HUD 表格数字字体：$it（需在当前系统安装）") }
            if (imported.hudReadingColors.isNotEmpty()) {
                Text("配色仅应用于 HUD 飞行与发动机表格；旧版其他文字和图形填充尚未迁移。RGBA 末两位为透明度。")
                val labels = mapOf("fontLabel" to "标签", "fontNum" to "普通读数", "fontUnit" to "单位", "fontWarn" to "告警读数", "fontShade" to "文字阴影")
                imported.hudReadingColors.forEach { (key, color) -> Text("HUD ${labels[key]}颜色：$color") }
            }
            imported.hiddenLabelChoices.forEach { (id, hidden) ->
                Text("HUD ${voidmei.telemetry.HudField.entries.first { it.id == id }.label}标签：${if (hidden) "隐藏" else "显示"}（保留数值）")
            }
            imported.hudCrosshairImage?.let { Text("图片准星：$it；保留外部文件引用，按旧版方形拉伸。") }
            imported.hudCrosshair?.let {
                if (imported.movesCrosshairRight) Text("${if (imported.hudCrosshairImage == null) "线框" else "图片"}准星：${if (it) "开启" else "关闭"}；新版位于 HUD 右侧。")
                else Text("准星：${if (it) "开启" else "关闭"}；保留当前样式与位置。")
            }
            if (imported.hudCrosshair == null && imported.movesCrosshairRight)
                Text("准星位置：HUD 右侧；保留当前开关。")
            imported.hudCrosshairSizeDp?.let { Text("${if (imported.hudCrosshairImage == null) "线框" else "图片"}准星跨度：$it dp；旧比例乘以 2，仅换算准星，不改变 HUD 字号或布局。") }
            imported.startInTray?.let { Text("下次启动进入托盘：${if (it) "开启" else "关闭"}；托盘不可用时显示主窗口，当前窗口保持不变。") }
            imported.recordingAutoStart?.let { Text("下次启动自动开启记录：${if (it) "开启" else "关闭"}；当前录制状态不变。") }
            imported.hudAoaWarningPercent?.let {
                Text("迎角数值预警阈值：$it%（旧值 0–1 按比例转换，1 表示 100%）")
            }
            imported.hudAoaBarWarningPercent?.let {
                Text("正迎角余量条预警阈值：$it%（旧值 0–1 按比例转换，1 表示 100%）")
            }
            imported.intervalMs?.let { Text("刷新间隔：$it ms") }
            imported.hudEnabled?.let { Text("HUD：${if (it) "开启" else "关闭"}") }
            imported.hudFlapBar?.let {
                Text("襟翼开度条：${if (it) "开启" else "关闭"}；新版受机械化区域总开关控制，与襟翼文字独立；关闭横条仍保留数值。")
            }
            imported.hudCompassHeadingUp?.let {
                Text("罗盘坐标系：${if (it) "航向朝上" else "北向朝上"}；姿态图：${if (it) "地面参考" else "机体参考"}。")
            }
            imported.hudAttitudeAoaLimits?.let {
                Text("HUD 姿态图迎角极限线：${if (it) "显示" else "隐藏"}；保留迎角读数、余量条和告警设置。")
            }
            imported.hudAttitude?.let {
                Text("HUD 姿态：${if (it) "开启" else "关闭"}")
                Text("姿态关闭时选择航向与罗盘，并按旧文字总开关合并；数据面板单独开启的航向仍保留。地图格号随航向显示，缺少有效地图或玩家位置时显示未知。")
            }
            listOf("起落架" to imported.hudGear, "襟翼/后掠及模型提示" to imported.hudFlaps,
                "减速板" to imported.hudAirbrake).forEach { (label, visible) ->
                visible?.let { Text("HUD $label：${if (it) "显示" else "隐藏"}") }
            }
            if (imported.hudGear != null || imported.hudFlaps != null || imported.hudAirbrake != null)
                Text("机械化子开关按旧文字总开关合并；保留当前机械化区域总开关。襟翼收起且 FM 确认为可变后掠翼时显示后掠；缺少襟翼数据时不推断为收起。")
            imported.hudAutoHideOnFocusLoss?.let {
                Text("切出游戏时隐藏 HUD：${if (it) "开启" else "关闭"}（目前仅 Windows 生效；实机验证待完成）")
            }
            if (imported.hudFieldChoices.isNotEmpty()) Text("旧 MiniHUD 与数据面板的对应数值合并到同一 HUD；MiniHUD 数值同时受文字总开关控制，同一字段只要任一旧面板最终显示，就保留显示。速度开关按 IAS/Mach 选择合并；缺省为显示 IAS。总开关仅用于已迁移数值，不代表指示条和布局已迁移。")
            imported.hudFieldChoices.forEach { (id, enabled) ->
                val label = voidmei.telemetry.HudField.selected(listOf(id)).singleOrNull()?.label ?: id
                Text("HUD $label：${if (enabled) "显示" else "隐藏"}")
            }
            if ("speed_limit_ratio" in imported.hudFieldChoices || "engine1_throttle" in imported.hudFieldChoices)
                Text("旧速度条/油门条按文字总开关与切换设置迁移为对应数值及横条。速度条使用遥测 Mach；油门固定采用 1 号发动机，缺失不借用其他编号。")
            if ("fuel_mass_share" in imported.hudFieldChoices) Text("旧机动性条迁移为燃油质量占比估计；它不是机动性能，不计弹药、外挂和损伤，需要匹配当前机型的 FM。")
            if ("aoa" in imported.hudFieldChoices) Text("迎角开关控制数值与距模型正迎角限的余量条；数值与横条使用独立预警阈值，姿态图的迎角/侧滑标记由姿态开关单独控制。")
            if ("roll_rate" in imported.hudFieldChoices) Text("Kotlin 滚转角速度保留方向正负号，区别于旧版绝对值。")
            if ("wing_sweep" in imported.hudFieldChoices) Text("Kotlin 后掠字段显示有效的 0%；无数据时显示 —，不再将两者一并隐藏。")
            if ("power" in imported.hudFieldChoices) Text("总功率显示所有已报告发动机的合计；有发动机功率缺失时显示 —，有效零值仍显示。")
            if (imported.hudFieldChoices.keys.any { it in listOf("wep_fuel", "wep_time") }) Text("WEP 燃料/续航显示上限：初始已用量未知，按 FM 容量减去观察到的油门 >100% 消耗。关闭 WEP 时续航未知；断流重新估算上限，不表示实际补满。")
            if (imported.hudFieldChoices.keys.any { it.startsWith("booster_fuel") }) Text("助推燃料沿用旧版燃油通道 1，保留有效零值；缺失时显示 —。百分比需有效正容量，最高显示 100%；该通道用途仍需逐机型核对。")
            if ("engine_response" in imported.hudFieldChoices) Text("响应速率表示显示动力量的变化（百分点/秒），并非 RPM 变化。按实际采样间隔平滑；换机、断流或峰值参考变化后重新计算。")
            if ("heat_tolerance" in imported.hudFieldChoices) Text("耐热时显示为 1 号发动机热预算范围，包含未知初始损耗，并非实际损坏倒计时。需要匹配 FM 和摄氏温度通道，无活动计时档位或数据缺失时显示 —。")
            if ("power_percent" in imported.hudFieldChoices) Text("动力量使用匹配 FM 的 WEP 功率或加力推力峰值，最大显示 100%。FM 不可用时，可在全部发动机连续全油门 5 秒后使用历史峰值，显示来源；未知发动机类型或缺失读数显示 —。")
            if ("propulsive_efficiency" in imported.hudFieldChoices) Text("桨效率导入为推进效率估计：总推力 × 真空速 / 总轴功率，沿用旧版 735 W/hp 参考值；不做中间整数截断。缺失数据或轴功率为零时显示 —，有效零效率保留。")
            if ("thrust_power" in imported.hudFieldChoices) Text("旧版实功率改为推进功率（kW）：总推力 × 真空速。有效零值显示 0；推力或真空速缺失时显示 —。")
            if ("endurance_clock" in imported.hudFieldChoices) Text("续航显示为分:秒；至少采样 10 秒，尚未形成估计时显示 —。漏油与抛弃油箱也会计入燃油变化。")
            if ("mass_estimate" in imported.hudFieldChoices) Text("旧版总重显示为质量估计：FM 空重 + 机油 + 最大加力燃料 + 当前燃油；不跟踪弹药、外挂或加力燃料消耗，FM 不匹配或数据缺失时显示 —。")
            if (imported.hudFieldChoices.keys.any { it in listOf("engine_temperature", "oil_temperature") })
                Text("温度优先显示仪表原值并标明来源；缺失时回退到 1 号发动机 °C。仪表单位与发动机对应关系尚未核实，不用于摄氏温度告警。")
            if ("engine1_manifold_auto" in imported.hudFieldChoices)
                Text("进气压力导入为自动推断单位：至少连续采样 10 秒且高度变化达到 10 m，按高度仪表比例选择 atm 或相对 1 atm 的 psi（附 inHg）。未确定时显示待判定，也可另选固定单位字段。")
            if (imported.hudFieldChoices.keys.any { it.startsWith("engine1_") })
                Text("旧版推力、转速与桨距对应 1 号发动机；不会取其他发动机代替，缺失时显示 —。")
            imported.alertVoices.forEach { (key, choice) ->
                Text("$key：${choice.pack} · ${if (choice.enabled) "开启" else "关闭"}")
            }
            imported.voiceVolume?.let { Text("语音音量：$it") }
            imported.voiceEnabled?.let { Text("语音：${if (it) "开启" else "关闭"}") }
            Button(enabled = imported.hasChanges, onClick = { onApply(imported); preview = null; status = "已应用，随 Kotlin 设置保存" }) { Text("应用预览设置") }
        }
        status?.let { Text(it) }
    }
}
