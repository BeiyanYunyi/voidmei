package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
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
    val settings = LegacySettingsReader.read(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
    val resolved = resolveLegacyRenderer(resolveLegacyCrosshair(settings, path, resourceRoot), path, resourceRoot)
    return resolveLegacyVoices(resolved, path, resourceRoot)
}

@Composable
fun LegacySettingsPanel(currentScene: HudSceneLayout? = null, chooseFile: (String) -> String? = ::chooseLegacySettingsFile,
    readSettings: (Path, Path?) -> LegacySettings = ::readLegacySettings, onApply: (LegacySettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("导入旧版设置") }
    if (!expanded) return
    var path by remember { mutableStateOf("ui_layout.user.cfg") }
    var resourceRoot by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<LegacySettings?>(null) }
    var source by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("迁移自动记录与托盘启动偏好、刷新间隔、已支持的 HUD 字段、姿态、机械化、准星、表格配色、迎角预警阈值及语音设置；具体变更见预览。可选按旧屏幕比例调整分区位置并补建缺少的对应分区；可另选迁移独立面板的显示、边框开关和姿态外框尺寸。旧窗口透明度与其他窗口尺寸尚不迁移。")
        OutlinedTextField(path, { path = it; preview = null; status = null }, Modifier.fillMaxWidth(),
            label = { Text("旧版布局文件路径（UTF-8）") }, enabled = !busy, singleLine = true)
        TextButton(enabled = !busy, onClick = {
            chooseFile(path)?.let { selected -> path = selected; preview = null; status = null }
        }) { Text("选择旧版设置文件") }
        OutlinedTextField(resourceRoot, { resourceRoot = it; preview = null; status = null }, Modifier.fillMaxWidth(),
            label = { Text("旧程序资源目录（留空使用配置所在目录）") }, enabled = !busy, singleLine = true)
        Text("图片查找位置：所选目录/image/gunsight/旧图片名.png。")
        Text("软件渲染优先读取所选目录/gpu_compat.properties；不存在时使用布局文件中的开关。")
        Text("语音查找位置：所选目录/voice/；导入保留外部文件引用，不复制语音包。")
        Button(enabled = !busy && path.isNotBlank(), onClick = {
            val selected = path
            val selectedRoot = resourceRoot.takeIf { it.isNotBlank() }
            preview = null
            status = null
            busy = true
            val request = ++generation
            readJob = scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { readSettings(Path.of(selected), selectedRoot?.let { Path.of(it) }) }
                    if (request == generation) { preview = result; source = selected }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (request == generation) status = "读取失败：${e.message}" }
                finally { if (request == generation) { busy = false; readJob = null } }
            }
        }) { Text("预览旧设置") }
        if (busy) TextButton(onClick = {
            generation++
            readJob?.cancel()
            readJob = null
            busy = false
            status = "已取消读取"
        }) { Text("取消读取") }
        preview?.let { imported ->
            Text("来源：$source")
            LegacyUnmigratedReport(imported.unmigrated)
            var importPositions by remember(imported, currentScene) { mutableStateOf(false) }
            var createMissing by remember(imported, currentScene) { mutableStateOf(false) }
            var engineCreationKeys by remember(imported, currentScene) { mutableStateOf(emptySet<String>()) }
            val engineSourceKeys = ((if (imported.engineControlStyle != null) setOf("enableEngineControl") else emptySet()) + imported.enginePanelFonts.keys + imported.enginePanelVisibility.keys + imported.enginePanelPositions.keys +
                (if (imported.engineReadingColumns != null || imported.powerTextSizes != null) setOf("engineInfoSwitch") else emptySet())).sorted()
            val engineCandidates = if (currentScene != null && currentScene.regions.size < 32)
                engineSourceKeys.associateWith { currentScene.legacyEngineRegion(it) } else emptyMap()
            val engineCreations = engineSourceKeys.filter { it in engineCreationKeys }.mapNotNull { engineCandidates[it] }
            val positionsNeedScreenSize = imported.hudPositions.values.any { it.needsScreenSize }
            val needsScreenSize = imported.enginePanelPositions.values.any { it.needsScreenSize } || positionsNeedScreenSize || imported.attitudeSize != null
            var screenWidth by remember(imported) { mutableStateOf("") }
            var screenHeight by remember(imported) { mutableStateOf("") }
            val parsedWidth = screenWidth.toIntOrNull()?.takeIf { it > 0 }
            val parsedHeight = screenHeight.toIntOrNull()?.takeIf { it > 0 }
            val screenSize = if (parsedWidth != null && parsedHeight != null) LegacyScreenSize(parsedWidth, parsedHeight) else null
            val positionsReady = !positionsNeedScreenSize || screenSize != null
            var sizeSelected by remember(imported, currentScene) { mutableStateOf(false) }
            var dpiInput by remember(imported) { mutableStateOf("") }
            val dpi = dpiInput.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 8 }
            val matched = currentScene?.regions.orEmpty().map { it.content }.toSet().intersect(imported.hudPositions.keys)
            val missing = imported.hudPositions.keys - matched
            val canCreate = currentScene != null && missing.isNotEmpty() && currentScene.regions.size + missing.size + engineCreations.size <= 32
            val positioned = imported.copy(engineRegionsToCreate = engineCreations, importHudPositions = positionsReady,
                createMissingHudRegions = createMissing && canCreate, legacyScreenSize = screenSize,
                importAttitudeSize = sizeSelected && screenSize != null && dpi != null, legacyDpiScale = dpi).applyToScene(currentScene)
            if (engineSourceKeys.isNotEmpty()) {
                Text("补建旧动力／控制分区")
                Text("使用 Kotlin 默认尺寸和外观，分别应用动力或控制字段预设；新动力区域显示表格，新控制区域隐藏表格并使用混合控制条。两者都读取 1 号发动机，可在 HUD 分区设置中修改编号；整机燃油等仍在飞行字段中配置。创建后请在下方选择要迁移的位置、显示开关或列数。")
                if (currentScene == null) Text("请先创建 HUD 分区布局，再重新预览。")
                engineSourceKeys.forEach { key ->
                    val checked = key in engineCreationKeys
                    val candidate = engineCandidates[key]
                    val used = (currentScene?.regions?.size ?: 32) + engineCreations.size +
                        (if (createMissing && canCreate && positionsReady) missing.size else 0)
                    val enabled = candidate != null && (checked || used < 32)
                    val name = if (key == "engineInfoSwitch") "动力信息" else "引擎控制"
                    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox,
                        onValueChange = { engineCreationKeys = if (it) engineCreationKeys + key else engineCreationKeys - key })
                        .testTag("legacy-create-engine-$key")) {
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        Text("新建${name}分区")
                    }
                    if (checked && candidate != null) Text("${candidate.title} · ${candidate.id}：${candidate.width} × ${candidate.height} dp，发动机 #1，${candidate.fields!!.size} 个字段")
                }
                if ((currentScene?.regions?.size ?: 0) + engineCreations.size >= 32) Text("已达到 32 个分区上限；可取消新建或删除已有分区。")
            }
            if (needsScreenSize) {
                    if (positionsNeedScreenSize) Text("检测到旧像素坐标：与 Java 一致，每个坐标轴大于 2 时按像素处理，其余按比例处理。")
                    Text("填写旧程序使用的主屏幕坐标宽高（与 Java Toolkit 的屏幕尺寸一致）；缩放显示器上可能与物理分辨率不同。原尺寸未知时可跳过位置和尺寸迁移，其他设置仍可导入。")
                    OutlinedTextField(screenWidth, { screenWidth = it }, singleLine = true,
                        label = { Text("旧屏幕宽度") }, isError = screenWidth.isNotEmpty() && parsedWidth == null,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(screenHeight, { screenHeight = it }, singleLine = true,
                        label = { Text("旧屏幕高度") }, isError = screenHeight.isNotEmpty() && parsedHeight == null,
                        modifier = Modifier.fillMaxWidth())
                    if (!positionsReady) Text("请输入正整数宽高后预览和迁移位置；当前不应用位置或补建分区。")
                }
            if (imported.hudPositions.isNotEmpty()) {
                val canImportPositions = positionsReady && (matched.isNotEmpty() || (createMissing && canCreate))
                Row(Modifier.fillMaxWidth().toggleable(value = importPositions, enabled = canImportPositions,
                    role = Role.Checkbox, onValueChange = { importPositions = it; if (!it) createMissing = false }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importPositions, onCheckedChange = null, enabled = canImportPositions)
                    Text("迁移已有分区位置")
                }
                Text(if (importPositions && canImportPositions) "位置预览：将应用" else "位置预览：未选择，不修改当前位置")
                Text("按当前画布比例换算旧屏幕坐标；每种类型只移动第一个分区，超出画布时移回边缘。保留分区尺寸、显示开关、透明度和显示器选择。")
                if (missing.isNotEmpty() && currentScene != null) {
                    Row(Modifier.fillMaxWidth().toggleable(value = createMissing, enabled = canCreate && positionsReady,
                        role = Role.Checkbox, onValueChange = {
                            createMissing = it
                            if (it) importPositions = true
                            else if (matched.isEmpty()) importPositions = false
                        }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(createMissing, onCheckedChange = null, enabled = canCreate && positionsReady)
                        Text("补建缺少的对应分区（${missing.size} 个）")
                    }
                    Text("新增分区先使用 Kotlin 默认尺寸、透明度和可见状态，继承全局字段；姿态尺寸可另选迁移。")
                    if (!canCreate) Text("补建后超过 32 个分区，请先删除不需要的分区。")
                }
                if (currentScene == null) Text("请先在 HUD 设置中创建分区布局，再重新预览。")
                imported.hudPositions.keys.forEach { content ->
                    val region = positioned?.regions?.firstOrNull { it.content == content }
                    Text(if (!positionsReady) "${content.label}：等待旧屏幕尺寸，位置未选择"
                        else if (region == null) "${content.label}：没有对应分区，跳过"
                        else "${content.label} → ${region.title.ifBlank { region.id }}：(${region.x}, ${region.y}) dp")
                    if (region != null && content in missing)
                        Text("新增 ${content.label}：${region.width} × ${region.height} dp，背景透明度 ${region.backgroundAlpha}")
                }
                Text("MiniHUD 位置需手动调整；动力信息和引擎控制可在下方明确选择目标后迁移位置。")
            }
            val attitudeRegion = positioned?.regions?.firstOrNull { it.content == HudRegionContent.ATTITUDE }
            val sizeReady = screenSize != null && dpi != null && attitudeRegion != null
            var importVisibility by remember(imported, currentScene) { mutableStateOf(false) }
            val visibleMatches = positioned?.regions.orEmpty().map { it.content }.toSet().intersect(imported.hudRegionVisibility.keys)
            var importBorders by remember(imported, currentScene) { mutableStateOf(false) }
            val borderMatches = positioned?.regions.orEmpty().map { it.content }.toSet().intersect(imported.hudRegionBorders.keys)
            var importSizes by remember(imported, currentScene) { mutableStateOf(false) }
            var importFont by remember(imported, currentScene) { mutableStateOf(false) }
            var importColumns by remember(imported, currentScene) { mutableStateOf(false) }
            val flightRegion = positioned?.regions?.firstOrNull { it.content == HudRegionContent.FLIGHT }
            var engineColumnsTarget by remember(imported, currentScene) { mutableStateOf<String?>(null) }
            val engineRegions = positioned?.regions.orEmpty().filter { it.content == HudRegionContent.ENGINE }
            val selectedEngineColumnsTarget = engineColumnsTarget?.takeIf { id -> engineRegions.any { it.id == id } }
            var enginePanelTargets by remember(imported, currentScene) { mutableStateOf(emptyMap<String, String>()) }
            val selectedEnginePanelTargets = enginePanelTargets.filterValues { id -> engineRegions.any { it.id == id } }
            var enginePositionTargets by remember(imported, currentScene) { mutableStateOf(emptyMap<String, String>()) }
            val selectedEnginePositionTargets = enginePositionTargets.filter { (key, id) ->
                engineRegions.any { it.id == id } && imported.enginePanelPositions[key]?.let { !it.needsScreenSize || screenSize != null } == true
            }
            var engineFontTargets by remember(imported, currentScene) { mutableStateOf(emptyMap<String, String>()) }
            val selectedEngineFontTargets = engineFontTargets.filterValues { id -> engineRegions.any { it.id == id } }
            var powerSizeTarget by remember(imported, currentScene) { mutableStateOf<String?>(null) }
            val selectedPowerSizeTarget = powerSizeTarget?.takeIf { id -> engineRegions.any { it.id == id } }
            var controlStyleTarget by remember(imported, currentScene) { mutableStateOf<String?>(null) }
            val selectedControlStyleTarget = controlStyleTarget?.takeIf { id -> engineRegions.any { it.id == id } }
            val selected = imported.copy(engineControlStyleRegionId = selectedControlStyleTarget, powerTextSizesRegionId = selectedPowerSizeTarget, engineFontTargets = selectedEngineFontTargets, engineRegionsToCreate = engineCreations, enginePositionTargets = selectedEnginePositionTargets, enginePanelTargets = selectedEnginePanelTargets, engineColumnsRegionId = selectedEngineColumnsTarget, importAttitudeSize = sizeSelected && sizeReady, legacyDpiScale = dpi, importHudRegionBorders = importBorders && borderMatches.isNotEmpty(), importFlightTextSizes = importSizes && flightRegion != null, importFlightLabelFont = importFont && flightRegion != null, importFlightReadingColumns = importColumns && flightRegion != null,
                importHudPositions = positionsReady && importPositions && (matched.isNotEmpty() || (createMissing && canCreate)),
                createMissingHudRegions = positionsReady && createMissing && canCreate, legacyScreenSize = screenSize,
                importHudRegionVisibility = importVisibility && visibleMatches.isNotEmpty())
            if (imported.hudRegionVisibility.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().toggleable(value = importVisibility, enabled = visibleMatches.isNotEmpty(),
                    role = Role.Checkbox, onValueChange = { importVisibility = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importVisibility, onCheckedChange = null, enabled = visibleMatches.isNotEmpty())
                    Text("迁移独立面板显示开关")
                }
                Text("兼容旧版独立开关项和面板总开关。每种类型只修改第一个对应分区，也可应用到本次补建的分区；保留全局 HUD、布局模式与 MiniHUD 姿态开关。未指定的类型保持当前状态。")
                val appliedScene = selected.applyToScene(currentScene)
                imported.hudRegionVisibility.forEach { (content, visible) ->
                    val region = appliedScene?.regions?.firstOrNull { it.content == content }
                    Text(if (region == null) "${content.label}显示开关：没有对应分区，跳过"
                        else "${content.label} → ${region.title.ifBlank { region.id }}：${if (visible) "显示" else "隐藏"}（${if (selected.importHudRegionVisibility) "将应用" else "未选择"}）")
                }
                if (visibleMatches.isEmpty()) Text("请先创建对应分区；文件含旧坐标时，也可勾选上方补建选项。")
            }
            imported.engineControlStyle?.let { style ->
                Text("迁移引擎控制字号与条形尺寸")
                Text("字号偏移 ${style.offset} → 条长 ${style.dimensions.lengthDp} dp、厚度 ${style.dimensions.thicknessDp} dp，文字 ${style.textSize} sp、粗体。面板 :font-size 优先，缺少时兼容引擎控制 fontSize 项。")
                Text("将目标区域的标签、数字、单位字号和字重一起设置，文字缩放设为 100%。按逻辑尺寸换算，系统显示缩放自动应用；保留字体名称、窗口大小和布局方向。旧窗口间距未复刻。")
                LegacyEngineTargetPicker(engineRegions, selectedControlStyleTarget, "legacy-control-style", "不迁移控制字号",
                    occupiedIds = setOfNotNull(selectedPowerSizeTarget)) { controlStyleTarget = it }
                engineRegions.firstOrNull { it.id == selectedControlStyleTarget }?.let { region ->
                    Text("引擎控制 → ${region.title.ifBlank { region.id }}：${style.dimensions.lengthDp} × ${style.dimensions.thicknessDp} dp，文字 ${style.textSize} sp（将应用）")
                }
            }
            imported.powerTextSizes?.let { sizes ->
                Text("迁移动力信息表格字号")
                Text("面板 :font-size 优先，缺少时兼容动力面板 fontSize 项。标签 ${sizes.label} sp、数字 ${sizes.number} sp、单位 ${sizes.unit} sp；标签和数字使用粗体，单位常规。")
                Text("将目标区域字体缩放设为 100%，避免重复放大；保留字体名称、窗口尺寸及其他区域。不还原旧控制条尺寸和布局。")
                LegacyEngineTargetPicker(engineRegions, selectedPowerSizeTarget, "legacy-power-size", "不迁移动力字号", occupiedIds = setOfNotNull(selectedControlStyleTarget)) { powerSizeTarget = it }
                engineRegions.firstOrNull { it.id == selectedPowerSizeTarget }?.let { region ->
                    Text("动力字号 → ${region.title.ifBlank { region.id }}：${sizes.label} / ${sizes.number} / ${sizes.unit} sp（将应用）")
                }
            }
            LegacyEngineFontSettings(imported.enginePanelFonts, engineRegions, selectedEngineFontTargets) { engineFontTargets = it }
            LegacyEnginePositionSettings(imported.enginePanelPositions, positioned, screenSize, selectedEnginePositionTargets) {
                enginePositionTargets = it
            }
            LegacyEngineVisibilitySettings(imported.enginePanelVisibility, engineRegions, selectedEnginePanelTargets) {
                enginePanelTargets = it
            }
            imported.engineReadingColumns?.let { columns ->
                Text("迁移动力信息列数：$columns 列")
                Text("选择要接收列数的发动机分区，仅修改该区域；不会更改字段、编号、位置或全局列数。")
                if (engineRegions.isEmpty()) Text("请先创建发动机分区，再重新预览。")
                LegacyEngineTargetPicker(engineRegions, selectedEngineColumnsTarget, "legacy-engine-columns", "不迁移列数") {
                    engineColumnsTarget = it
                }
                engineRegions.firstOrNull { it.id == selectedEngineColumnsTarget }?.let { region ->
                    Text("${region.title.ifBlank { region.id }}：${region.readingColumns?.let { if (it == 0) "自动列数" else "$it 列" } ?: "继承全局"} → $columns 列（将应用）")
                }
            }
            imported.flightReadingColumns?.let { columns ->
                Row(Modifier.fillMaxWidth().toggleable(value = importColumns, enabled = flightRegion != null,
                    role = Role.Checkbox, onValueChange = { importColumns = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importColumns, onCheckedChange = null, enabled = flightRegion != null)
                    Text("迁移飞行信息列数")
                }
                Text(if (flightRegion == null) "旧飞行信息为 $columns 列；请先创建飞行读数分区，或选择本次补建。"
                    else "飞行信息 → ${flightRegion.title.ifBlank { flightRegion.id }}：$columns 列（${if (selected.importFlightReadingColumns) "将应用" else "未选择"}）")
                Text("仅修改第一个飞行读数分区；保留全局列数、其他分区与字段顺序。多列适合较宽分区。")
            }
            imported.flightLabelFont?.let { font ->
                Row(Modifier.fillMaxWidth().toggleable(value = importFont, enabled = flightRegion != null,
                    role = Role.Checkbox, onValueChange = { importFont = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importFont, onCheckedChange = null, enabled = flightRegion != null)
                    Text("迁移飞行标签字体")
                }
                Text(if (flightRegion == null) "旧飞行标签字体为 $font；请先创建飞行读数分区，或选择本次补建。"
                    else "飞行标签 → ${flightRegion.title.ifBlank { flightRegion.id }}：$font（${if (selected.importFlightLabelFont) "将应用" else "未选择"}）")
                Text("飞行面板 :font 优先；未指定时兼容历史 flightInfoFontC 设置项。只修改第一个飞行分区的表格标签字体；数字字体、字号与其他分区保持原配置。")
                val resolved = remember(font) { resolveTextFont(font) }
                if (resolved.unavailable) Text("当前系统未安装 $font，标签将使用系统默认字体。")
                Text("速度 高度 发动机", fontFamily = resolved.family)
            }
            imported.flightTextSizes?.let { sizes ->
                Row(Modifier.fillMaxWidth().toggleable(value = importSizes, enabled = flightRegion != null,
                    role = Role.Checkbox, onValueChange = { importSizes = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importSizes, onCheckedChange = null, enabled = flightRegion != null)
                    Text("迁移飞行表格字号")
                }
                Text("基础字号：标签 ${sizes.label}、数字 ${sizes.number}、单位 ${sizes.unit} sp")
                Text(if (flightRegion == null) "请先创建飞行读数分区，或选择本次补建。"
                    else "${flightRegion.title.ifBlank { flightRegion.id }} 的文字缩放将设为 100%（${if (selected.importFlightTextSizes) "将应用" else "未选择"}）")
                Text("飞行面板字号属性优先，缺少属性时采用该面板的历史字号行。仅转换旧飞行面板的字号偏移，仍受系统字体缩放影响。区域尺寸、其他分区与全局设置保持原配置；文字较大时请增大区域。标签和数字使用粗体，单位使用常规字重；窗口边框尚不转换。")
            }
            if (imported.hudRegionBorders.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().toggleable(value = importBorders, enabled = borderMatches.isNotEmpty(),
                    role = Role.Checkbox, onValueChange = { importBorders = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(importBorders, onCheckedChange = null, enabled = borderMatches.isNotEmpty())
                    Text("迁移独立面板边框开关")
                }
                val borderScene = selected.applyToScene(currentScene)
                imported.hudRegionBorders.forEach { (type, enabled) ->
                    val region = borderScene?.regions?.firstOrNull { it.content == type }
                    Text(if (region == null) "${type.label}边框：没有对应分区，跳过"
                        else "${type.label} → ${region.title.ifBlank { region.id }}：边框${if (enabled) "开启" else "关闭"}（${if (selected.importHudRegionBorders) "将应用" else "未选择"}）")
                }
                Text("每种类型仅修改第一个对应分区，也支持本次补建。使用新版边框效果，保留边框透明度和区域尺寸；旧 WebLaf 阴影效果尚未还原；姿态外框尺寸可另选迁移。")
            }
            imported.attitudeSize?.let { size ->
                Text("旧姿态内容尺寸：${size.width} × ${size.height}；旧边框${if (size.border) "开启" else "关闭"}。缺省宽／高按 Java 的 150／300 处理。")
                OutlinedTextField(dpiInput, { dpiInput = it }, singleLine = true,
                    label = { Text("原 DPI 缩放倍率（例如 1 或 1.5）") },
                    isError = dpiInput.isNotEmpty() && dpi == null, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().toggleable(value = sizeSelected, enabled = sizeReady,
                    role = Role.Checkbox, onValueChange = { sizeSelected = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(sizeSelected, onCheckedChange = null, enabled = sizeReady)
                    Text("迁移姿态窗口尺寸")
                }
                if (!sizeReady) Text("当前不迁移姿态尺寸。填写原屏幕坐标宽高与 DPI 缩放，并创建姿态分区后可预览尺寸；也支持本次补建。未知原尺寸时可跳过此项。")
                else {
                    val previewRegion = selected.copy(importAttitudeSize = true).applyToScene(currentScene)?.regions?.firstOrNull { it.content == HudRegionContent.ATTITUDE }
                    val outer = size.outerSize(dpi!!)
                    Text("旧外框：${outer.first} × ${outer.second}；按原屏幕比例换算到当前画布。")
                    previewRegion?.let {
                        Text("姿态尺寸 → ${it.title.ifBlank { it.id }}：${it.width} × ${it.height} dp，位置 (${it.x}, ${it.y})（${if (selected.importAttitudeSize) "将应用" else "未选择"}）")
                    }
                }
                Text("仅修改第一个姿态分区的外框。尺寸限制在画布内且不小于 80 × 40 dp，必要时位置移回边缘。新版内边距、标题与图形排布保持原样，不是旧姿态图的像素级复刻；边框开关可另选迁移。")
            }
            if (!selected.hasChanges) Text(if (imported.hudPositions.isEmpty() && imported.hudRegionVisibility.isEmpty() && imported.engineControlStyle == null && imported.powerTextSizes == null && imported.enginePanelFonts.isEmpty() && imported.enginePanelPositions.isEmpty() && imported.enginePanelVisibility.isEmpty() && imported.engineReadingColumns == null && imported.flightReadingColumns == null && imported.flightLabelFont == null && imported.flightTextSizes == null && imported.hudRegionBorders.isEmpty() && imported.attitudeSize == null)
                "此文件没有可应用的设置。" else "此文件没有已选择的可应用设置。")
            if (imported.hudEngineFieldChoices.isNotEmpty()) {
                Text("引擎控制开关应用到全局发动机读数字段；保留当前发动机编号、面板开关及分区独立字段。尚未开启发动机读数时，请在 HUD 设置中选择发动机编号。")
                imported.hudEngineFieldChoices.forEach { (id, enabled) ->
                    val field = voidmei.telemetry.HudEngineField.entries.first { it.id == id }
                    Text("发动机 ${field.label}：${if (enabled) "显示" else "隐藏"}")
                }
                if ("rpm_control" in imported.hudEngineFieldChoices)
                    Text("旧引擎控制“桨距”对应转速控制百分比，不是桨叶角度。")
            }
            imported.softwareRendering?.let {
                Text("软件渲染：${if (it) "开启" else "关闭"}（重启后生效）；保留当前 HUD 兼容显示选择。")
                Text("渲染设置来源：${imported.softwareRenderingSource ?: "布局文件 gpuCompatibilityMode"}")
                Text("启动参数或 SKIKO_RENDER_API 环境变量仍优先于此设置。")
            }
            imported.numberFont?.let { Text("全局数字字体：$it（需在当前系统安装）") }
            imported.textFont?.let { Text("全局文字字体：$it（需在当前系统安装）") }
            imported.httpPort?.let { Text("遥测端口：$it；保留已保存的主机地址，点击“连接”后切换当前连接。") }
            imported.hudAltitudeMode?.let { Text("HUD 高度来源：${it.label}（雷达单位未判定时使用海拔）") }
            imported.hudNumberFont?.let { Text("HUD 表格数字字体：$it（需在当前系统安装）") }
            if (imported.hudReadingColors.isNotEmpty()) {
                Text("配色应用于主窗口与 HUD 飞行、发动机表格；旧版其他文字和图形填充尚未迁移。RGBA 末两位为透明度。")
                val labels = mapOf("fontLabel" to "标签", "fontNum" to "普通读数", "fontUnit" to "单位", "fontWarn" to "告警读数", "fontShade" to "文字阴影")
                imported.hudReadingColors.forEach { (key, color) -> Text("表格默认及 HUD ${labels[key]}颜色：$color") }
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
            imported.recordingPerformanceNotifications?.let { Text("高度档及机动采样通知：${if (it) "开启" else "关闭"}") }
            imported.connectionNotifications?.let { Text("8111 状态托盘通知：${if (it) "开启" else "关闭"}") }
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
            imported.hudAttitudeNorthPointer?.let {
                Text("HUD 姿态图指北针：${if (it) "显示" else "隐藏"}；红色指北、白色指南，保留航向读数和独立罗盘设置。")
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
            imported.voiceDirectory?.let {
                Text("语音目录：$it；确认后使用此目录中的外部语音文件，请保留该目录。")
                Text("单条告警的包名和开关保持导入值。缺失文件沿用默认回退；导入时不播放声音，确认后可在语音设置中试听。")
            }
            imported.voiceVolume?.let { Text("语音音量：$it") }
            imported.voiceEnabled?.let { Text("语音：${if (it) "开启" else "关闭"}") }
            Button(enabled = selected.hasChanges, onClick = {
                try { onApply(selected); preview = null; status = "已应用，随 Kotlin 设置保存" }
                catch (e: IllegalArgumentException) { status = "应用失败：${e.message}" }
            }) { Text("应用预览设置") }
        }
        status?.let { Text(it) }
    }
}
