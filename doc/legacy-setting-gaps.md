# Java 配置剩余差异清单

2026-09-12，使用当前 KMP `LegacySettingsReader` 读取仓库自带 `ui_layout.cfg`。返回 **31 条未迁移记录、26 个不同标识（含 target 和面板属性名）**；`fontSize` 在多个面板出现。此数字表示该文件的配置导入差异，不能直接解释成 26 个未实现功能，也不覆盖用户自定义文件中的全部格式。

## 按实际含义推进

| 类别 | 旧标识 | 当前判断与下一步 |
| --- | --- | --- |
| 各窗口字号、字体和列数 | `fontSize`、`fontName`、`hudColumns` | Kotlin 已支持 1–16 列，旧 `flightInfoColumn` 可选迁移至第一个飞行读数分区；动力面板 `hudColumns` 可选迁移至用户明确选定的发动机分区。飞行／发动机分区已支持独立读数字体，旧飞行 `:font`／`flightInfoFontC` 可选迁移；动力／控制面板 `:font` 及动力面板历史 `fontName` 已支持可选迁移到明确指定区域的标签字体。剩余旧键具有面板作用域，字号偏移还涉及窗口和图形尺寸。需要按所属面板解析，再映射字号和列数，不能把多个 `fontSize` 合并成一个值。 |
| 动力与引擎控制独立窗口 | `engineInfoSwitch`、`enableEngineControl` | 旧窗口与当前 FLIGHT／ENGINE 分区结构不同，已提供发动机“动力读数／引擎控制”独立字段预设，可手动分成两个区域；发动机分区已可独立启用整机燃油百分比与水平燃油条。两个显示开关已支持显式选择不同发动机分区后迁移；两个旧窗口的位置也可显式选择不同发动机分区后迁移；导入预览已支持明确选择新建两个区域并套用字段预设；标签字体与动力表格字号已支持可选迁移；已增加可独立保存的横排／竖排／混合布局与表格显示选项，新建控制区域默认无表格混合仪表布局；已支持独立控制条长度与厚度；旧控制字号已可选转换为条长、厚度和文字样式；窗口尺寸、旧间距及整机／逐发动机读数差异仍待补齐，不能据此宣称窗口已对齐。 |
| 姿态窗口刷新 | `attitudeIndicatorFreqMs` | 独立窗口刷新频率与当前遥测轮询不同，需核对节流策略。外框宽高已经支持按原屏幕与 DPI 可选换算；内部图形排布仍有差异。 |
| 姿态旧颜色选项 | `attitudeIndicatorUseNumColor` | 在当前 Java `AttitudeOverlay` 中只给 `transParentWhite` 赋值，未找到绘制读取。先核实有效行为，不添加无效果开关来缩短清单。 |
| FM 原始信息窗口 | `enableFMPrint`、`displayFmKey` | Kotlin 已有原始字段筛选面板（`ModelFieldsPanel`）；旧独立窗口开关及显示键还需核对行为后迁移。 |
| 模型选择和曲线状态 | `selectedFM0`、`selectedFM1`、`powerCurveSpeed`、`powerCurveWep` | 需核对旧机型标识、路径以及曲线输入的含义，明确如何恢复到当前模型会话。 |
| 模型分类显示 | `showMaxLiftLoad`、`showLift`、`showDrag` | 当前有 18 类模型详情显示选择，支持 13 类旧显示意图迁移。五类器件参数已按来源与后掠配置分别展示，迎角是未扣安装角的原值；Fin／Stab 路由保留旧字段组名称并明确路径。剩余三类包含派生量，需核对质量、面积、单位及指定工况；显示开关可迁移不等于旧数值完全一致。 |
| 全局语音包选择 | `globalVoicePack` | Java 的选择动作批量修改单条语音配置；Kotlin 已有语音 ZIP 安装和单条语音迁移。不能把保存的全局选择直接覆盖已迁移的单条选择。 |
| 绘制与调试 | `AAEnable`、`enableLayoutDebug` | Java 的渲染选项／MiniHUD 布局调试与 Compose 后端不同，需对照实际功能，不直接映射为软件渲染或用户 HUD 开关。 |
| 操作按钮 | `openComparison`、`openPowerCurve`、`importConfig`、`factoryReset` | 这些是操作入口，不是读取旧配置时应自动执行的动作。比较、导入及恢复默认已有 Kotlin 入口，应单独验收对应功能。 |
| 表格外的颜色 | `fontNum`、`fontLabel`、`fontUnit`、`fontWarn`、`fontShade` | 表格默认与 HUD 配色已经迁移；此处的剩余记录特指其他图形、填充和文字，不应重复覆盖已迁移部分。 |

源码核对入口：[Java 配置](../ui_layout.cfg)、[Java 姿态窗口](../src/ui/overlay/AttitudeOverlay.java)、[Java 全局语音操作](../src/ui/layout/renderer/VoiceGlobalRenderer.java)、[Kotlin 导入器](../core/src/commonMain/kotlin/voidmei/config/LegacySettings.kt)。

## 重新生成清单

只读工具：[LegacySettingsInventory.java](../script/fixtures/LegacySettingsInventory.java)。它调用 KMP 导入器，只打印差异，不启动旧程序、不执行配置中的按钮动作、不写入用户设置。

Linux 开发环境中先生成当前类文件与依赖目录：

```bash
nix develop path:. --command gradle -Pvoidmei.systemNode=true :desktop:createDistributable
nix develop path:. --command java --class-path 'core/build/libs/core-jvm.jar:desktop/build/compose/binaries/main/app/VoidMei/lib/app/*' script/fixtures/LegacySettingsInventory.java ui_layout.cfg
```

本轮使用当前 `core/build/libs/core-jvm.jar`，并从已经构建的独立包提供 Kotlin 运行依赖执行工具，结果位于 `/tmp/voidmei-aerodynamic-parts-inventory.tsv`，统计日志 `/tmp/voidmei-aerodynamic-parts-inventory.log`。这些临时路径不作为仓库的永久数据源，生产代码或旧配置改变后应重新生成。

## 当前集成验证

生产源码 `8ea750f5` 共享 JVM 571、JS 568、桌面单元 151、完整 GUI 393 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-model-details-integrated.log`。独立包 `/tmp/voidmei-kmp-model-details` 已通过隔离 Linux 启动、保存渲染器设置、AWT 心跳及正常退出验证，报告 `/tmp/voidmei-package-smoke-d2wfwska/report.json`。实际游戏与跨平台验证仍未完成。

## 历史集成验证

多列增量之前的完整 GUI 回归 **142 类、338 项**，无失败、错误或跳过，耗时约 64 秒。使用隔离 Xvfb、xcompmgr 和软件渲染；日志 `/tmp/voidmei-integrated-settings-gui.log`。覆盖近期导入、撤回、目录跳转、姿态指北针及既有窗口交互，不代表真实游戏或 Windows／macOS 验收。

多列增量共享 JVM 519、JS 516、桌面单元 151 项通过；相关 GUI 28 项通过，覆盖自定义输入、窄区域、多分区独立列数、旧导入预览与撤回。三列截图已检查。日志 `/tmp/voidmei-multicolumn-validation.log`、`/tmp/voidmei-multicolumn-gui.log`。

分区字体增量：共享 JVM 523、JS 520、桌面单元 151 项与相关 GUI 25 项通过，实际两分区字体隔离、恢复继承及截图已验证。日志 `/tmp/voidmei-region-font-final.log`。当前 Java 字体实际路径已追踪至 `ConfigurationService.getFontName()` 与 `RenderContext.fromSettings()`，面板 `:font` 优先；历史设置项保留为可选兼容来源。

## 引擎控制布局的后续依据

`EngineControlOverlay.initGaugeFields()` 实际采用混合方向：油门、桨距控制、动力量为竖条；混合比、散热器、增压器和燃油为横条。`drawGauges()` 的条长为 `4 * fontsize`，厚度为 `fontsize >> 1`，横条行距为 `fontsize + (fontsize >> 2)`。Kotlin 现已提供混合布局，按上述方向绘制对应逐发动机控制条；整机燃油现可在发动机分区独立启用，保持水平条并明确整机归属；旧燃油开关已支持显式选择现有或新建发动机分区后迁移。旧字号现已支持逻辑尺寸与文字样式换算；后续仍需处理间距、逐像素 DPI 取整，混合方向本身不代表整个旧窗口已经复刻。

## 字号差异的具体依据

Java `RenderContext.create()` 使用数字字号 `24 + fontAdd`，标签与单位字号为其一半四舍五入；`ConfigurationService.getFontSizeAdd()` 从面板 `:font-size` 读取。KMP 紧凑表格当前基准为标签 13 sp、数字 14 sp，单位沿用数字字号。例如 Java 偏移 +6 得到数字 30、标签／单位 15，不能通过一个统一的 KMP 文字倍率同时还原。也不能将超出当前倍率上限的数字字号静默截断。当前已分别支持标签、数值与单位的基础字号和对应宽度测量，并可选转换飞行面板 `:font-size`；字重现已随飞行字号可选迁移，边框和窗口尺寸仍需继续核对。旧行 `fontSize` 和面板 `:font-size` 应区分保存语义。

本轮新增的精确百分比输入用于调整现有 KMP 文字比例，不宣称完成旧字号迁移。源码依据：`src/ui/renderer/RenderContext.java`、`src/prog/config/ConfigurationService.java`、`desktop/src/main/kotlin/voidmei/desktop/FlightReadings.kt`。

精确字号增量之后，当前生产源码完整 GUI 回归 145 类、345 项通过；共享 JVM 523、JS 520、桌面单元 151 项通过。日志 `/tmp/voidmei-font-integrated.log`。精确字号没有新增旧键映射，清单仍为 53 条记录、47 个不同 target。


独立基础字号增量已支持飞行面板 `:font-size` 的 -6–20 范围，保持数字与标签／单位各自尺寸，不按统一倍率强行截断。导入会明确预览并把第一个飞行分区的文字缩放设为 100%。`fontSize` 历史设置行、其他面板字号与窗口几何仍在差异清单中。飞行字号迁移现已包含标签／数字粗体与单位常规字重，分区也可单独配置。只读 target 清点不统计面板属性，因此该功能不减少 53 条记录／47 个 target 的计数。

本轮独立基础字号最终验证：共享 JVM 525、JS 522、桌面单元 151 项通过，完整 GUI 146 类、348 项通过。日志 `/tmp/voidmei-reading-sizes-final-tests.log` 与 `/tmp/voidmei-reading-sizes-integrated-gui.log`；实际大小和分列截图已检查。

字重增量最终验证：共享 JVM 527、JS 524、桌面单元 151 项通过，完整 GUI 147 类、349 项通过。日志 `/tmp/voidmei-reading-weights-integrated.log`，两分区实际字重截图已检查。此轮没有新增旧 target 映射，清点仍为 53 条记录／47 个不同 target。


## 边框开关已经支持，几何差异继续保留

`flightInfoEdge`、`enableAxisEdge`、`enableAttitudeIndicatorEdge`、`enablegearAndFlapsEdge` 已支持可选迁移到每类第一个分区，采用 KMP 区域内的阴影和细线边框；保持区域尺寸、读数位置及当前边框透明度。UI 明确提示效果适配而非旧 WebLaf 阴影复刻。

Java `FieldOverlay` 调用 `setShadeWidth(10)`，实际绘制由项目自带 WebLaf `WebRootPaneUI` 和 `NinePatchIcon` 完成；姿态等独立窗口还显式增加 20 像素外沿。因此不能仅映射开关便宣称旧窗口尺寸已对齐。后续仍需针对各类窗口的实际图形尺寸、DPI 和外沿计算做预览转换。只读 javap 核对记录在 `/tmp/voidmei-weblaf-rootpane.txt`。

边框增量验证：共享 JVM 529、JS 526、桌面单元 151 项及相关 GUI 15 项通过；实际边框像素和几何不变已验证，最终截图已检查。日志 `/tmp/voidmei-region-border-final-gui.log`。

迁移联动与边框集成回归：当前生产源码完整 GUI 149 类、352 项通过，日志 `/tmp/voidmei-import-dependencies-integrated.log`。清单未新增映射，仍为 49 条记录／43 个不同 target。


姿态外框尺寸已支持：读取旧内容宽高、原 DPI 和屏幕坐标尺寸，计入 4 像素固定留白及可选的 20 像素边框外沿，再按屏幕比例换算当前画布。只作用于第一个姿态分区，并在位置应用前调整尺寸。需要用户在预览中提供原屏幕信息；其他窗口尺寸和旧图形内部布局仍待处理。

姿态尺寸增量验证：共享 JVM 532、JS 529、桌面单元 151 项、相关 GUI 17 项通过；最终预览截图已检查。日志 `/tmp/voidmei-attitude-size-final-gui.log`。


## 透明度属性报告补全

四类独立 HUD 的 `:alpha` 属性原先没有出现在报告中，现在明确列出，并保留当前 KMP 背景／内容透明度。源码中未发现这些窗口使用该保存属性绘制；详细链路见 [旧透明度核对](legacy-alpha-audit.md)。因此本轮记录从 47 增加到 51，标识从 41 增加到 42，属于报告覆盖扩大。下方及前文按轮记录的旧计数为历史结果，以页首当前计数为准。

报告搜索增量后的完整回归：共享 JVM 534、JS 531、桌面单元 151 项，完整 GUI 152 类、355 项通过。日志 `/tmp/voidmei-settings-search-integrated.log`。报告可搜索全部记录并分批显示；本轮未改变映射范围，仍为 51 条记录、42 个标识。


## 飞行字号行的面板作用域

“飞行信息”面板内的历史 `fontSize` 行现在支持可选迁移，包含嵌套分组；显式 `:font-size` 属性优先。其他面板的同名行不进入全局设置映射，仍保留为未迁移记录。Java 的 `RendererConfigHelper`／`PropertyBinder` 按所属 `GroupConfig` 读写此行，KMP 因此按面板识别，不能把所有 `fontSize` 合并成一个值。

当前清点少一条飞行字号记录，仍有其他面板的 `fontSize`，所以不同标识数量不变。共享 JVM 536、JS 533、桌面单元 151、相关 GUI 21 项通过。日志 `/tmp/voidmei-scoped-font-size-tests.log`、`/tmp/voidmei-scoped-font-size-final-gui.log`。


## 未迁移项目来源路径

报告保留面板与嵌套分组路径，界面可显示和搜索，清点工具第三列也输出来源。当前五条未迁移 `fontSize` 分别位于：MiniHUD / 外观设置、动力信息 / 外观设置、引擎控制 / 发动机元素、舵面值 / 显示设置、起落襟翼 / 显示设置。不能因为名称相同便视为同一设置。

共享 JVM 538、JS 535、桌面单元 151、相关 GUI 18 项通过，来源搜索截图已检查。日志 `/tmp/voidmei-report-source-tests.log`、`/tmp/voidmei-report-source-gui.log`。本轮只补全报告来源，没有新增迁移映射，仍为 50 条记录、42 个标识。
