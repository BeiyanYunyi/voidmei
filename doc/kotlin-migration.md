# Kotlin 重写验收状态

2026-09-12，根据当前工作区源码及本机执行结果核对。此页区分已有实现、尚未实现与尚未验证；不以测试总数代表完整替换。

## 三舵 PowerLoss 原始系数（2026-09-12）

对照 Java `Blkx` 使用的 `AileronPowerLoss`、`ElevatorPowerLoss`、`RudderPowerLoss` 补齐共享提取与模型页显示。保存有限数值原值，包括零和符号；根路径精确匹配优先，嵌套来源唯一时才回退。非有限、错误类型、重复和歧义字段保持未知并记录问题。“舵效”分类显示三位小数和原始字段名，明确旧“锁舵因数”不代表实时锁舵状态或百分比，不接入实时告警。

共享 JVM 565、JS 562、桌面单元 151、相关 GUI 32 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-control-power-loss-final.log`。覆盖符号和零值、路径优先、缺失／非法／重复／歧义解析、未知显示清除、实际模型加载和分类隐藏恢复，并回归模型计算与曲线。首次回归发现新增字段的异常在问题列表快照之后才收集，已改为先提取再构造参数；空 BLK 夹具改成有效但不含系数的文档后重跑通过。

本轮未打包。未增加旧配置开关映射，差异记录仍为 39 条；其余 11 个模型分类及其他差异继续推进。

## 模型襟翼限速数据点表（2026-09-12）

补齐模型详情中的襟翼限速数据点表，复用共享提取器验证和排序后的真实节点，开度转换为百分比，速度标为模型 IAS km/h，显示保留两位小数。每次展开 8 点，支持全部 64 点；模型数据变化后恢复首批。无有效节点时明确提示并清除旧表，不补造档位。原有当前开度插值和当前 IAS 下最大开度读数保留，数据表随“襟翼与起落架”分类一起显示／隐藏。

桌面单元 151、相关 GUI 5 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-model-flap-table-tests.log`。覆盖真实提取的乱序节点、百分比与小数显示、标量单点、无效／缺失数据清除、全部 64 点可达、换模型分页重置，以及模型页分类隐藏恢复和既有迁移／后掠模型回归。共享解析和插值逻辑未修改；旧分类迁移的提示已更新为包含数据点表。

本轮未打包，旧配置未迁移记录仍为 39 条。本次补齐显示内容，不新增旧键映射；旧锁舵因数和其余模型分类仍待推进。

## 旧模型显示分类可选迁移（2026-09-12）

支持将 `showWeight`、`showCritSpeed`、`showGLoadLimits`、`showFlapLimits`、`showControlEffectiveness` 的显示意图迁移到新版对应分类。默认不勾选，预览逐项列出映射及差异；仅更新文件中出现的分类，保留其他显示选择。临界速度只对应速度／迎角限制，不迁移旧无效数值，也不改变独立失速估算。过载使用新版估算；襟翼分类还包含起落架；舵效当前只有衰减速度。这些说明直接显示在导入预览中，不将分组映射等同于旧数值和内容完全复刻。

共享 JVM 563、JS 560、桌面单元 151、相关 GUI 30 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-legacy-model-categories-tests.log`。覆盖所有五类 switch／switch-inv 极性组合、默认不应用、增量合并、幂等与 JSON 往返、未知分类保留报告、非法／重复配置、界面勾选取消与应用及既有导入回归。

真实旧配置清点降至 39 条记录、34 个不同标识，结果 `/tmp/voidmei-legacy-model-categories-inventory.tsv` 和 `/tmp/voidmei-legacy-model-categories-inventory.log`。本轮未打包。其余 11 个旧分类开关及旧襟翼数据点表、锁舵因数等内容仍需继续处理。

## 曲线分类隐藏时保留输入（2026-09-12）

修复隐藏模型分类后功率／推力曲线交互状态丢失。按当前加载模型和燃油计算建立保存范围，每个发动机曲线使用独立键，保存展开状态、功率 TAS／EAS、速度、温度、高度探查、推力高度／速度，以及功率条件未应用草稿与校验提示。分类重新显示后恢复交互输入，派生曲线重新计算；模型加载身份或计算变化后重建范围，避免将旧模型输入带到新机型。

桌面单元 151、相关 GUI 28 项通过，无失败、错误或跳过。最终日志 `/tmp/voidmei-curve-retention-final.log`。端到端测试通过真实文件提取模型，反复隐藏／恢复两种曲线，验证功率草稿和错误提示、速度类型、已应用条件、4000 米探查，以及推力 5000 米／500 km/h；隐藏期间切换机型后两种曲线恢复默认。首次测试将功率探查误当成 0–1 比例，按实际米制量程修正后全部通过；相关模型计算回归同时通过。

本轮未修改共享逻辑或重建软件包。该状态保留用于当前模型页面的分类隐藏恢复，不宣称跨应用重启保存曲线输入；旧 Java 分类开关映射仍待继续处理。

## 模型详情显示分类（2026-09-12）

新增 10 个显示分类：重量、襟翼与起落架、舵效、失速估算、过载限制、速度与迎角限制、发动机耐热、活塞功率与曲线、喷气推力与曲线、原始字段。隐藏集合写入应用配置，默认全部显示，支持恢复全部；主窗口与离线模型页共用设置。模型提取、告警及比较数据不读取显示选择，隐藏后保留已发布参数及模型来源、加载状态、实例对应和参数提取问题。

共享 JVM 561、JS 558、桌面单元 151、相关 GUI 26 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-model-sections-tests.log` 和最终 `/tmp/voidmei-model-sections-final.log`。覆盖配置往返、旧配置默认和非法类型、隐藏单类／全部、恢复显示、原始字段移除与恢复，以及隐藏期间已发布参数对象与回调次数不变；既有模型计算、加载和后掠过载相关 GUI 回归通过。

本轮未打包。分类按 Kotlin 当前内容划分，Java 的 16 个分类开关尚未逐项映射，旧配置清点仍为 44 条记录。曲线组件内部的临时输入在隐藏再显示后会重建，下一步需保留该交互状态；不可将当前显示分类视为旧 FM 窗口完整替代。

## 控制布局与燃油迁移集成验收（2026-09-12）

对生产源码提交 `c72b64a7` 运行完整共享／桌面／GUI 检查并构建独立 Nix 包，覆盖此前尚未一起打包的混合布局、表格开关、仪表精度、控制条尺寸、旧控制字号换算、整机燃油及旧燃油开关目标迁移。共享 JVM 560、JS 557、桌面单元 151、完整 GUI 169 类 383 项通过，无失败、错误或跳过。未变化的 Gradle 任务允许复用已验证结果；完整 GUI 任务实际执行，日志 `/tmp/voidmei-control-fuel-integrated.log`。

独立包 `/tmp/voidmei-kmp-control-fuel` 指向 `/nix/store/h69vlr2a3lsv67sfqlya6wc3pv0s1257-voidmei-kotlin-2.0.0`，构建日志 `/tmp/voidmei-control-fuel-package.log`。隔离 X11 整包验证报告 `/tmp/voidmei-package-smoke-lvn4ot4y/report.json` 确认主窗口及兼容 HUD 读取保存的 SOFTWARE_FAST 偏好、AWT 心跳、设置保留、窗口位置保存及正常退出码 0。日志 `/tmp/voidmei-control-fuel-smoke.log`。这证明当前 Linux 主机上的上述行为，不证明真实游戏、全部界面响应、语音播放、物理关闭按钮或 Windows／macOS 行为。

使用当前共享 jar 和最新包依赖重新执行旧配置清点，仍为 44 条记录、39 个不同标识；结果 `/tmp/voidmei-control-fuel-inventory.tsv` 与 `/tmp/voidmei-control-fuel-inventory.log`。本次没有新增业务功能；剩余差异仍按清单继续推进。

## 旧控制燃油开关的分区迁移（2026-09-12）

`disableEngineInfoLFuel` 现在除了原有全局飞行燃油字段迁移，还提供可选的发动机分区目标，将显示／隐藏状态写入 `showAircraftFuel`。默认不选择目标；支持现有分区及本次新建的引擎控制区域。预览列出目标当前状态与即将应用的状态，并说明原有飞行字段迁移仍会应用。保留其他分区、发动机编号、字段、布局、表格和图形开关。

共享 JVM 560、JS 557、桌面单元 151、相关 GUI 30 项通过，无失败、错误或跳过，最终日志 `/tmp/voidmei-legacy-engine-fuel-final.log`。覆盖两种旧开关类型及布尔极性、默认不改场景、显式目标隔离、无效目标跳过、新建区域应用、重复应用、JSON 往返、选择取消与旧导入回归。首次编译因局部候选列表声明顺序失败，修正后完整重跑上述范围通过。

本轮未重建软件包。此开关此前已纳入飞行字段解析，所以不会减少未迁移配置记录数；本次补齐的是旧控制窗口燃油显示的分区目标。窗口间距和逐像素 DPI 取整仍待继续对齐。

## 发动机分区整机燃油余量（2026-09-12）

新增独立“显示整机燃油余量”开关，默认关闭并兼容旧 JSON。燃油来自整机余量百分比，不归属于所选发动机；所选发动机缺失或关闭发动机读数表格时仍可显示。数值保留一位小数，水平条满刻度 100%，沿用分区条形尺寸及文字样式；关闭附带图形后保留数值和告警。零值有效，负数、非有限值和缺失数据不绘制条形，并显示缺失原因。

共享 JVM 558、JS 555、桌面单元 151、相关 GUI 7 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-engine-aircraft-fuel-tests.log` 与最终文字单位样式回归 `/tmp/voidmei-engine-aircraft-fuel-final.log`。覆盖 JSON 往返和类型校验、设置分区隔离、实际场景开关、发动机缺失时整机数据保留、小数精度、零油告警、无效数据清除、关闭图形保留数值及既有飞行燃油显示回归。

本轮未重建软件包。旧燃油显示开关尚未自动迁移，新建旧控制区域仍需手动开启此项；旧窗口间距和逐像素 DPI 取整继续待补齐。

## 旧引擎控制字号自动换算（2026-09-12）

读取引擎控制面板 `:font-size` 和历史 `fontSize` 项（-6–20 整数），属性优先。以 24 加偏移为基准，将条长设为四倍、厚度为整数除二，文字为半字号取整并设为粗体。默认不应用；明确选择目标发动机分区后，一起更新条形尺寸、标签／数字／单位字号与字重，并将文字缩放设为 100%。保留字体名称、位置、窗口尺寸、布局方向和显示开关。支持本次新建区域，禁止动力字号与控制字号同时指定同一目标。

共享 JVM 557、JS 554、桌面单元 151、相关 GUI 32 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-legacy-control-size-tests.log`。覆盖全部 27 个合法偏移、奇数字号的取整区别、属性优先、非法／重复设置、新建区域、目标类型检查、尺寸预览和冲突禁选、JSON 往返及既有导入和尺寸编辑回归。原先用引擎控制字号代表未支持项的作用域／报告测试，改用尚未迁移的 MiniHUD 字号并保留验证目的。真实旧配置清点为 44 条记录、39 个不同标识，见 `/tmp/voidmei-legacy-control-size-inventory.tsv`。

本轮未重建软件包。换算使用逻辑 dp／sp 并应用当前系统缩放；旧窗口间距、逐像素 DPI 取整和整机燃油归属未完全复刻，整体目标仍继续推进。

## 分区控制条长度与厚度（2026-09-12）

发动机分区新增可选控制条尺寸，长度 48–512 dp、厚度 8–48 dp，两项验证通过后一起应用。横向油门、连续控制条及增压器刻度使用所选长度和占用高度，竖条使用对应高度／宽度；横条宽度受区域约束。标签字体与分区窗口大小独立。旧 JSON 缺少尺寸时保留原有自适应行为，恢复默认清除整组尺寸覆盖。尺寸变化重置分区滚动适配。

共享 JVM 555、JS 552、桌面单元 151、相关 GUI 12 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-control-dimensions-tests.log`、`/tmp/voidmei-control-dimensions-final-tests.log`。覆盖 JSON 往返、旧配置默认、非法值、输入一起应用与恢复、实际横竖尺寸和另一分区隔离。Material 进度条的语义区域额外扩展高度，测试改为读取着色像素验证绘制厚度；没有用语义区域高度替代实际条形尺寸。截图 `desktop/build/hud-preview/engine-control-dimensions.png` 已检查。

本轮未重建软件包。尺寸设置为后续旧引擎控制字号换算提供了明确目标；旧字号的自动转换、间距及整机燃油仍需继续处理。

## 纯仪表数值精度与量程边界（2026-09-12）

修复控制条数值固定取整导致 FM 动力量丢失小数的问题：横排、竖排和混合布局均使用字段本身的显示精度，FM 动力量保留一位小数，与表格一致。横向控制条的无障碍状态同时提供当前值及满刻度；竖条状态使用相同精度。条形仍按量程限制，油门和混合比超过满刻度时保留实际显示数值。

桌面单元 151、相关 GUI 13 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-instrument-precision-tests.log`。新测试遍历三种布局，验证 75.36% 显示为 75.4%、进度仍使用未四舍五入值、125% 油门和 150% 混合比的数值保留，以及零值、负值、非有限值和数据消失的处理。既有表格显示与控制条回归同时通过。

本轮未重建软件包；旧窗口字号与图形尺寸联动仍待补齐。

## 发动机表格与仪表独立显示（2026-09-12）

ENGINE 分区新增“显示读数表格”，旧布局缺省保持 true。隐藏表格后继续显示选中的仪表、标题、有效告警及缺失数据说明；告警使用警告色，数据失效后移除旧告警。横向仪表补充当前数值，横排油门增加独立数值说明，避免去除表格后只剩进度。字段选择、仪表显示和表格显示分别保留；纯数值字段需开启表格查看。

旧配置导入中新建的控制区域默认隐藏表格、启用混合布局；新动力区域保留表格。已有区域不自动改变。共享 JVM 554、JS 551、桌面单元 151 项通过；最终相关 GUI 10 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-engine-table-tests.log`、`/tmp/voidmei-engine-table-final-tests.log`。覆盖 JSON 往返、旧配置默认、创建默认值、分区隔离、图形保留、缺失值与告警更新、横条数值及设置开关。截图 `desktop/build/hud-preview/engine-readings-hidden.png` 已检查。

本轮未重建软件包；旧窗口字号与图形尺寸联动、整机燃油显示仍待继续对齐。

## 发动机控制条混合布局（2026-09-12）

分区布局改为横排／竖排／混合三种互斥选择，使用枚举持久化；兼容先前 `engineControlsVertical` 布尔字段，无布局字段时仍为横排，新枚举显式指定时优先。混合布局将油门、转速控制与动力量放入竖条组，混合比与水／油散热器横排，增压器保留水平刻度。原量程、FM 来源说明、缺失值规则保持不变；方向切换继续重置分区滚动适配。

共享 JVM 552、JS 549、桌面单元 151、相关 GUI 11 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-mixed-controls-tests.log`。覆盖各模式 JSON 往返、旧布尔值兼容和非法字段、三模式互斥、单区域隔离、各字段方向、油门不重复绘制、量程及缺失值清除。截图 `desktop/build/hud-preview/mixed-engine-controls.png` 已检查。

本轮未重建软件包。混合方向已支持；旧控制条字号／尺寸联动、整机燃油归属与完整旧窗口几何仍需继续处理。

## 发动机布局改动集成验收（2026-09-12）

当前源码完整回归：共享 JVM 551、JS 548、桌面单元 151、GUI 369 项通过（162 个 GUI 测试类），无失败、错误或跳过。日志 `/tmp/voidmei-engine-layout-integrated.log`。覆盖近期动力字号、标签字体迁移、目标搜索、仪表标签样式和竖排控制条，以及既有导入、撤回和 HUD 窗口流程。

新包 `/tmp/voidmei-kmp-engine-layout`（`/nix/store/cig2ng7v47n0y2781f1as11bfsy9cha5-voidmei-kotlin-2.0.0`）已构建成功，构建日志 `/tmp/voidmei-engine-layout-package.log`。隔离 Linux 启动、保存的软件渲染器、AWT 心跳及正常退出验证通过，报告 `/tmp/voidmei-package-smoke-4fff38oe/report.json`。不代表实际游戏连接、Windows／macOS 或完整旧窗口布局验收。当前旧配置清点仍为 45 条未迁移记录、39 个不同标识，日志 `/tmp/voidmei-engine-layout-inventory.log`。

后续核对确认 Java 控制窗口实际采用混合方向：油门／桨距／动力量竖排，混合比／散热器／增压器／燃油横排，条长与字号联动。已在差异清单记录源码依据；当前全竖排选项不替代该混合布局的迁移。

## 发动机连续控制条竖排（2026-09-12）

ENGINE 分区新增独立保存的“连续控制条竖排”选项，默认 false，旧 JSON 缺少该字段时保持横排。支持油门、转速控制、动力量、混合比、水／油散热器竖条，宽度不足时换行；增压器档位保留水平刻度。量程沿用 110%／120%／100%，无效或缺失值不绘制，实际数值与条形进度分开显示。关闭附带图形保留方向选择，方向切换重置分区滚动适配。

共享 JVM 551、JS 548、桌面单元 151、相关 GUI 9 项通过，无失败、错误或跳过。日志 `/tmp/voidmei-vertical-controls-tests.log`。覆盖 JSON 往返、旧配置默认、非法布尔值、单分区开关隔离、隐藏后保留方向、同画布横竖对照、量程与缺失值；原有控制条和增压器建议回归同时通过。截图 `desktop/build/hud-preview/vertical-engine-controls.png` 已检查。

本轮未重建软件包。此选项补齐连续控制条布局能力，尚未自动转换旧引擎控制字号及其图形尺寸，也不代表旧窗口整套布局已一一复刻。

## 发动机仪表说明继承分区标签样式（2026-09-12）

核对 Java 控制条后发现，Kotlin 的表格已经应用分区标签样式，但控制条说明、增压器刻度与建议仍直接使用界面默认样式。现统一读取当前分区的标签字体、字号和字重，并覆盖发动机标题及说明文字；没有独立设置时保留各元素默认样式。数字字体和字重独立，控制条数据与刻度逻辑不变。分区设置及旧字体迁移说明同步标明实际覆盖范围。

桌面单元 151 项通过，控制条、增压器建议、分区字体、字号和字重相关 GUI 回归通过，日志 `/tmp/voidmei-engine-label-tests.log`。最终说明更新后再跑 4 项字体设置／渲染 GUI，全部通过，日志 `/tmp/voidmei-engine-label-final-gui.log`。新测试读取真实 TextLayoutResult，验证标题、散热器说明和增压器刻度说明的字体／字号／字重、另一分区隔离、数字样式保留及清除覆盖后恢复默认。截图 `desktop/build/hud-preview/engine-instrument-label-fonts.png` 已检查。

本轮未重建软件包。旧控制条的竖排布局、图形尺寸与字号联动仍需进一步迁移；本轮修复的是已配置标签样式未作用到实际仪表说明的问题。

## 动力表格字号迁移（2026-09-12）

按 Java `RenderContext.fromSettings` 的规则迁移动力信息表格：数字为 24 加偏移，标签／单位为其一半取整，标签与数字粗体、单位常规。支持动力面板 `:font-size` 及作用域内 `fontSize` 项（-6–18 整数），属性优先。默认不应用；明确选定已有或本次新建的发动机分区后，应用字号、字重并将该区域字体缩放设为 100%，保留字体名称、区域尺寸和其他区域。引擎控制的旧字号同时决定图形尺寸，仍待单独处理。

共享 JVM 550、JS 547、桌面单元 151、相关 GUI 30 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-power-size-tests.log`。新测试覆盖全部 25 个合法偏移、属性优先、跨面板作用域、非法和重复值、JSON 往返、新建目标与取消依赖；既有字号／字重渲染测试同时通过。原先把动力字号当作未支持示例的作用域与报告测试，已改用尚未迁移的引擎控制字号，保留原验证目的。真实旧配置未迁移项为 45 条、39 个不同标识，见 `/tmp/voidmei-power-size-inventory.tsv`。

本轮未重建软件包；旧窗口整体尺寸和控制条字号仍未对齐。

## 发动机迁移目标搜索与分批显示（2026-09-12）

位置、显示开关、列数和标签字体迁移统一使用目标选择器，明确显示分区名称、ID 和发动机编号。候选超过六项时支持按名称／ID／编号的多关键词搜索，默认展示六项并可继续展开；当前选中项始终可见，即使不匹配搜索也不取消迁移。清除搜索恢复首批候选。保留其他窗口占用目标的禁选，以及像素坐标屏幕尺寸前置条件。

桌面单元 151、相关 GUI 27 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-engine-target-tests.log`。新测试使用 32 个同名分区验证 ID／编号搜索、分页、保留选择、无匹配结果、冲突禁选和取消目标；原有各项导入回归同时通过。截图 `desktop/build/hud-preview/legacy-engine-target-search.png` 已检查，选中的 31 号发动机在搜索 30 号时仍保留，30 号因其他窗口占用而禁用。本轮不改变共享导入规则，未重建软件包。

## 动力与控制标签字体迁移（2026-09-12）

Java `FieldOverlay` 的 `RenderContext.fromSettings` 和 `EngineControlOverlay.loadFontConfig` 使用面板字体作为标签字体。Kotlin 现可读取两个面板的 `:font`，动力面板缺少该属性时兼容历史 `fontName` 项；其他面板同名键仍保留作用域报告。默认不应用，用户明确选择不同的目标发动机分区后，仅修改 `readingLabelFont`；数字字体、字号、全局字体和其他分区保持原值。字体文件缺失时沿用现有默认字体回退。仅字体配置也可预览并选择新建相应区域。

共享 JVM 548、JS 545、桌面单元 151、相关 GUI 26 项通过，无失败、错误或跳过。覆盖属性优先、历史项回退、同名键作用域、非法和重复字体、目标冲突与取消、区域隔离、JSON 往返、新区域导入及现有分区字体渲染。日志 `/tmp/voidmei-engine-font-tests.log`。实际旧配置清点为 46 条未迁移记录、39 个不同标识，见 `/tmp/voidmei-engine-font-inventory.tsv`。本轮未重建软件包，上一轮 `/tmp/voidmei-kmp-engine-import` 不包含此字体迁移改动。

## 导入时创建动力与控制分区（2026-09-12）

导入预览可明确选择新建动力信息和引擎控制分区，使用共享字段预设及 Kotlin 默认尺寸和外观。两者默认读取 1 号发动机，不推断飞机拥有第二台发动机；编号可在分区设置中修改。新区域先创建，再接收显式选择的位置、显示开关和列数迁移。取消创建会过滤指向已取消区域的迁移目标。总分区数不得超过 32，不覆盖已有分区；同一导入结果重复应用不会重复添加相同 ID。新的导入预览中再次明确选择新建，则产生新区域。

默认位置尽量将动力与控制区域横向分开，较窄画布采用错位；原屏幕坐标可另选迁移。旧窗口完整尺寸、字体、整机与逐发动机读数差异仍未全部对齐。

创建流程完整 GUI 回归 363 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-engine-create-full-gui.log`。此完整回归在最终默认位置错开调整前执行；最终调整另跑共享及创建界面回归。共享 JVM 546、JS 543、桌面单元 151、创建 GUI 1 项均通过，无失败、错误或跳过，日志 `/tmp/voidmei-engine-create-placement-tests.log`。

新包 `/tmp/voidmei-kmp-engine-import`（`/nix/store/70qgmmblrv8w6bywzr9rgp13av3rpqrm-voidmei-kotlin-2.0.0`）已构建，包含最近几轮列数、独立开关、位置迁移及本轮创建流程。隔离 Linux 启动、保存的软件渲染器、AWT 心跳和正常退出均验证通过，报告 `/tmp/voidmei-package-smoke-x0o7ptkb/report.json`。未以此代替实际游戏与跨平台验证。

## 动力与引擎控制位置迁移（2026-09-12）

读取两个旧窗口各自的坐标并可选映射到不同的已有 ENGINE 分区。默认不迁移，位置与显示开关分别选择目标；预览显示当前坐标到应用坐标，目标冲突禁选。复用其他旧窗口的按轴像素／比例转换和边缘限制，保留目标尺寸、字段、显示状态及其他属性。缺少旧屏幕尺寸时停用像素坐标选择，其他比例坐标仍可导入。尚未自动创建两个旧窗口对应分区或恢复其完整样式。

共享 JVM 544、JS 541、桌面单元 151、相关 GUI 22 项通过，无失败、错误或跳过。包含混合坐标、边缘限制、缺省坐标轴、重复／非法坐标、目标类型与冲突校验、JSON 往返、像素尺寸前置条件及预览一致性。日志 `/tmp/voidmei-engine-position-tests.log`。真实 `ui_layout.cfg` 正常解析，未迁移报告仍为 47 条、39 个不同标识；坐标之前不计入该报告，不能用该数量判断本轮位置功能是否完成。

本轮未重建软件包；最新改动需从当前源码运行。

## 动力与引擎控制独立显示开关（2026-09-12）

旧 `engineInfoSwitch` 与 `enableEngineControl` 可分别映射至明确选择的 ENGINE 分区，默认不应用。兼容显式开关项及面板 `:switch-key`／`:visible`，沿用显式项优先规则。预览显示原状态和导入状态，禁用被另一个旧窗口占用的目标，取消选择即可释放；共享逻辑拒绝重复目标。仅修改可见状态，保留全局 HUD、字段、编号、位置和其他分区，尚不自动创建或移动旧窗口对应区域。

共享 JVM 542、JS 539、桌面单元 151、相关 GUI 23 项通过，无失败、错误或跳过。覆盖两个窗口独立导入、默认不应用、目标类型与重复目标、非法值、显式项优先、取消映射、界面冲突禁选、JSON 往返和既有导入流程。日志 `/tmp/voidmei-engine-visibility-tests.log`。真实旧配置清点为 47 条未迁移记录、39 个不同标识，见 `/tmp/voidmei-engine-visibility-inventory.tsv`；该数量不代表完整功能差距。

本轮未重建软件包，`/tmp/voidmei-kmp-engine-presets` 不含本轮及上一轮列数迁移改动。

## 动力窗口列数的显式目标迁移（2026-09-12）

读取“动力信息”面板内 `hudColumns`（1–8 整数），保留其他面板同名键的未迁移报告。导入预览要求明确选择已有 ENGINE 分区，默认不迁移；显示当前列数到新列数的变化并允许取消。只修改所选区域列数，保留全局列数、字段、编号、位置与其他区域。不自动创建动力窗口或迁移其开关。

共享 JVM 540、JS 537、桌面单元 151、相关 GUI 20 项通过，无失败、错误或跳过。验证范围包含作用域、非法与重复值、默认不应用、选择与取消目标、目标类型检查、区域隔离和 JSON 往返。日志 `/tmp/voidmei-engine-columns-tests.log`、`/tmp/voidmei-engine-columns-related-gui.log`。实际旧配置清点为 49 条未迁移记录、41 个不同标识，见 `/tmp/voidmei-engine-columns-inventory.tsv`。本轮未重建软件包，上一轮包不包含此改动。

## 发动机动力与控制字段预设（2026-09-12）

全局发动机字段和 ENGINE 分区新增“动力读数／引擎控制／默认字段”预设，替换对应字段及顺序后仍可逐项编辑。区域应用自动开启独立字段，保留编号、位置和其他外观属性；关闭独立字段可恢复继承。整机燃油等仍在飞行读数中选择，旧窗口开关、位置及自动创建尚未迁移。

完整回归：共享 JVM 538、JS 535、桌面单元 151、GUI 359 项通过，无失败、错误或跳过。包含前两轮作用域字号和报告来源改进；新测试验证区域属性保留、全局选择不受区域预设影响、后续逐项删除和恢复继承。日志 `/tmp/voidmei-engine-presets-full.log`。

新包 `/tmp/voidmei-kmp-engine-presets`（`/nix/store/0x01lp8kr55hajas75m7vg8jg6mlp291-voidmei-kotlin-2.0.0`）构建成功。在隔离 Linux 显示环境中，保存的软件渲染器、AWT 心跳和正常退出验证通过；报告 `/tmp/voidmei-package-smoke-jt0sw_wh/report.json`。不代表实际游戏或跨平台验证。

## 未迁移报告来源定位（2026-09-12）

未迁移记录新增面板与嵌套分组来源。普通未支持项目、面板属性，以及能从原设置键追踪的后续验证报告均保留路径。界面显示来源并支持来源关键词搜索，可区分同名同标识条目；清点工具输出旧标识、说明、来源三列。导入行为和选择不变。

共享 JVM 538、JS 535、桌面单元 151、相关 GUI 18 项通过，无失败、错误或跳过。验证覆盖嵌套路径、面板属性、延后验证报告、相同名称／标识的来源区分和多关键词搜索。日志 `/tmp/voidmei-report-source-tests.log`、`/tmp/voidmei-report-source-gui.log`，截图 `desktop/build/hud-preview/unmigrated-sources.png` 已检查。

当前清点仍为 50 条记录、42 个标识，输出 `/tmp/voidmei-report-source-inventory.tsv`。本轮未重建包，下方搜索设置包不包含近期飞行字号行与来源定位增量；未新增真实游戏或跨平台验收。

## 飞行字号历史行兼容（2026-09-12）

旧“飞行信息”面板的 `fontSize` 行可作为 `:font-size` 属性缺省时的字号来源，支持嵌套分组；明确属性优先。解析保留面板作用域，其他面板同名行继续报告，不修改动力或操纵面。应用仍需独立选择，沿用第一飞行分区的字号／字重转换与撤回流程。

共享 JVM 536、JS 533、桌面单元 151、相关 GUI 21 项通过，无失败、错误或跳过。覆盖 -6–20 全范围、嵌套分组、不同面板隔离、属性优先、重复／无效行，以及两种格式的独立 GUI 应用流程。日志 `/tmp/voidmei-scoped-font-size-tests.log`、`/tmp/voidmei-scoped-font-size-final-gui.log`。

当前清点 50 条未迁移记录、42 个标识，日志 `/tmp/voidmei-scoped-font-inventory.log`。本轮未重建独立包，下方搜索设置包不包含这项历史格式增量；未扩大真实游戏或跨平台验收范围。

## 未迁移报告搜索与集成交付（2026-09-12）

未迁移报告新增全量搜索和每次 25 项的分批显示，替代原先仅显示前 100 项的限制。支持名称／旧标识、多关键词与英文大小写不敏感匹配；不同面板的同名标识分别保留。收起报告保留查询和加载进度，清除搜索恢复初始显示，搜索不影响导入选择。

当前源码共享 JVM 534、JS 531、桌面单元 151 项通过，完整 GUI 152 类、355 项通过，无失败、错误或跳过。验证覆盖第 100 项之后的记录、分页加载、同名属性、多关键词、收起保留、空结果和清除搜索，并包含近期姿态尺寸及透明度报告回归。日志 `/tmp/voidmei-settings-search-integrated.log`，截图 `desktop/build/hud-preview/unmigrated-search.png` 已检查。未新增真实游戏或其他系统验收。

最新 Linux 独立包：`/tmp/voidmei-kmp-settings-search`，实际输出 `/nix/store/g6ykyjk5bimx4s122dv31qs8sd2aiklq-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-settings-search/bin/voidmei-kotlin --no-hud` 可试用，包含姿态尺寸迁移、旧透明度报告与报告搜索。隔离 Xvfb 验证主窗口／兼容 HUD 使用保存的软件渲染偏好、AWT 心跳、设置保存和正常退出通过，退出码 0。构建日志 `/tmp/voidmei-settings-search-package.log`，运行日志 `/tmp/voidmei-settings-search-smoke.log`，证据 `/tmp/voidmei-package-smoke-q1xy8oni/`。

## 旧透明度属性核对与报告补全（2026-09-12）

追踪四类独立 HUD 的 `:alpha` 后，未发现 Java 绘制路径读取该保存属性；与另一个 `BaseOverlay` 的自有透明度字段分开处理，详见 [源码核对](legacy-alpha-audit.md)。导入器现在明确报告这些未转换属性，提示在分区设置中分别调整背景和内容；不改变任何当前透明度，不阻塞其他有效设置。仅含属性的文件也能预览原因。

共享 JVM 534、JS 531、桌面单元 151、相关旧导入 GUI 16 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-alpha-report-validation.log`。验证覆盖四类面板、其他设置同时导入、属性名与普通标签区分、重复／缺值属性及应用禁用状态。

当前清点为 51 条记录、42 个不同标识（含旧 target 与面板属性名），比上轮多出的四条是原先静默忽略的透明度属性；不表示功能回退。日志 `/tmp/voidmei-alpha-inventory.log`。本轮未重建包，也未新增实际游戏或跨平台验收；下方已构建包不包含近期姿态尺寸和本轮报告增量。

## 姿态外框尺寸迁移（2026-09-12）

旧姿态宽高支持独立可选迁移，需要填写原屏幕坐标宽高和原 DPI 缩放。按 Java 规则计算含固定留白和边框外沿的旧外框，再按屏幕比例换算当前画布，预览目标尺寸与位置。只调整第一个姿态分区；先补建、改尺寸，再应用位置，避免被原默认尺寸提前限制坐标。尺寸限制在当前画布及区域最小尺寸内。

共享 JVM 532、JS 529、桌面单元 151 项通过，相关 GUI 17 项通过。覆盖 DPI 与四舍五入、边框外沿、缺省维度、极小／极大目标、补建定位顺序、无效输入、预览一致性、持久化与撤回。最终日志 `/tmp/voidmei-attitude-size-validation.log`、`/tmp/voidmei-attitude-size-final-gui.log`；截图 `desktop/build/hud-preview/legacy-attitude-size.png` 已检查。

当前旧配置清点为 47 条记录、41 个不同 target，日志 `/tmp/voidmei-attitude-size-inventory.log`。姿态内部留白、标题与图形仍使用 KMP 布局，外框比例转换不代表旧图像像素级复刻。其他窗口尺寸、透明度及 WebLaf 阴影仍待处理。本轮未重建独立包，下方边框设置包不含此尺寸增量。

## 迁移联动提示与边框集成交付（2026-09-12）

位置迁移支持整行点击，坐标预览明确标注是否会应用。组合测试验证：取消位置迁移同时取消补建，依赖新增飞行分区的列数、字体、字号／字重和边框不会执行；恢复补建后这些选择一起应用，既有分区不变。测试也验证补建后的画布边界限制。没有发现误建分区的生产缺陷，本轮改进是点击范围和状态说明。

当前生产源码完整 GUI 149 类、352 项通过，无失败、错误或跳过；共享 JVM 529、JS 526、桌面单元 151 项通过（未改动的任务复用 Gradle 结果）。日志 `/tmp/voidmei-import-dependencies-integrated.log`。这轮完整 GUI 包含前轮边框改动，验证环境仍为隔离 Xvfb／软件渲染。

最新 Linux 独立包：`/tmp/voidmei-kmp-bordered-settings`，实际输出 `/nix/store/85frwi99gwjhnwi7r3gr09fsyw057m62-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-bordered-settings/bin/voidmei-kotlin --no-hud` 可试用，包含分区边框及迁移联动提示。隔离 Xvfb 验证主窗口／兼容 HUD 使用保存的软件渲染偏好、AWT 心跳、设置保存和正常退出通过，退出码 0。构建日志 `/tmp/voidmei-bordered-settings-package.log`，运行日志 `/tmp/voidmei-bordered-settings-smoke.log`，证据 `/tmp/voidmei-package-smoke-k5cdmzzu/`。未新增真实游戏或跨平台验收。

## 分区边框与旧开关适配（2026-09-12）

任意分区可开启边框并独立调整不透明度，使用现有区域内留白，不移动读数、不改变区域尺寸。旧飞行、操纵面、姿态和机械化边框开关支持独立可选迁移，每类仅修改第一个对应分区，也支持本次补建；保留边框透明度、其他分区和几何配置，并可撤回。预览明确采用新版效果，旧 WebLaf 阴影和窗口外沿尺寸未作像素级复刻。

共享 JVM 529、JS 526、桌面单元 151 项通过；最终相关 GUI 10 类、15 项通过，无失败、错误或跳过。实际像素测试验证边框可见、透明度为 0 时隐藏、读数与区域位置不变，旧 JSON 兼容、四类开关与撤回均通过。截图 `desktop/build/hud-preview/region-border.png` 已检查，并根据截图改善了边框与文字的间距。日志 `/tmp/voidmei-region-border-validation.log`、`/tmp/voidmei-region-border-final-gui.log`。

当前导入器读取 `ui_layout.cfg` 得到 49 条未迁移记录、43 个不同 target，日志 `/tmp/voidmei-region-border-inventory.log`。本轮未重建独立包，下方读数样式包不包含此边框增量；本轮运行的是相关 GUI，不能将前轮完整 GUI 结果视为当前源码的完整回归。

## 表格字重与旧飞行样式（2026-09-12）

飞行／发动机分区新增独立标签、数字、单位字重，支持常规、粗体和恢复默认；单位可跟随数字。默认不改变现有主题字重，恢复字重不会清除字体与字号。宽度测量采用实际字重，改变字重会重置自动分列的历史宽度。

旧飞行表格字号的可选迁移现在同时恢复 Java 字重：标签 700、数字 700、单位 400。预览说明这项变更，未选择时保持当前配置，撤回可恢复导入前的自定义字重。旧边框和窗口尺寸仍待转换。

共享 JVM 527、JS 524、桌面单元 151 项通过；最终完整 GUI 147 类、349 项通过，无失败、错误或跳过。验证了两分区实际字重隔离、单位覆盖／继承、恢复默认、旧 JSON 兼容、迁移选择及撤回。截图 `desktop/build/hud-preview/reading-text-weights.png` 已检查。日志 `/tmp/voidmei-reading-weights-validation.log`、`/tmp/voidmei-reading-weights-integrated.log`。

最新 Linux 独立包：`/tmp/voidmei-kmp-reading-style`，实际输出 `/nix/store/nw8frf49xbqxq5nsjc0azcgnl2f13c2g-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-reading-style/bin/voidmei-kotlin --no-hud` 可试用，包含独立基础字号、字重与旧飞行样式迁移。隔离 Xvfb 检查主窗口／兼容 HUD 使用保存的软件渲染偏好、AWT 心跳、设置保存和正常退出通过，退出码 0。构建日志 `/tmp/voidmei-reading-style-package.log`，运行日志 `/tmp/voidmei-reading-style-smoke.log`，证据 `/tmp/voidmei-package-smoke-uaj8zonf/`。这不代表真实游戏或其他系统的运行验收。

## 独立基础字号与旧飞行字号转换（2026-09-12）

飞行／发动机分区新增标签、数字和单位各自的基础字号（6–64 sp），一起验证应用，并可恢复默认。绘制与宽度测量使用相同的单位字号；修改字号后重置自动分列宽度历史，避免小单位仍占用旧大字号的空间。其他分区与图形保留原样。

飞行面板 `:font-size` 支持 -6–20 整数偏移的独立可选迁移：数字为 24 加偏移，标签／单位取一半四舍五入；预览说明会把第一个飞行分区文字缩放设为 100%。支持持久化与撤回，不改全局、其他分区和区域尺寸。旧 `fontSize` 设置行、字重、边框及窗口尺寸仍未转换。

共享 JVM 525、JS 522、桌面单元 151 项通过；最终生产源码完整 GUI 146 类、348 项通过，无失败、错误或跳过。验证了实际字号／单位文本样式、两分区隔离、缩小单位后的自动分列、非法输入、恢复默认与导入预览。截图 `desktop/build/hud-preview/separate-reading-sizes.png` 已检查。日志 `/tmp/voidmei-reading-sizes-final-tests.log`、`/tmp/voidmei-reading-sizes-integrated-gui.log`。

本轮没有重建独立包；下方区域字体包不包含此基础字号增量。GUI 使用隔离 Xvfb／xcompmgr 和软件渲染，未新增实际游戏或其他系统验收。

## 精确字号与完整字体集成回归（2026-09-12）

全局 HUD 和所有分区增加可收起的精确字号输入，范围 75–200%，最多两位小数；保留滑块和快捷档位。分区显示实际比例及继承状态，非法输入不应用，独立分区尚未应用的输入不会被全局字号变化清除。真实读数测量验证了分区独立缩放、恢复继承与区域几何尺寸保持。

本轮在当前生产源码上执行完整 GUI：145 类、345 项全部通过，无失败、错误或跳过；共享 JVM 523、JS 520、桌面单元 151 项通过（未改动部分任务复用 Gradle 结果）。完整命令日志 `/tmp/voidmei-font-integrated.log`，包含前轮字体、多列和旧导入功能的组合回归。仍使用隔离 Xvfb／xcompmgr 与软件渲染，不代表真实游戏或跨平台验收。

最新 Linux 独立包：`/tmp/voidmei-kmp-region-fonts`，实际输出 `/nix/store/61swgfmb012jki6ljfdh3zbn3khv5axb-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-region-fonts/bin/voidmei-kotlin --no-hud` 可试用，包含独立读数字体、旧飞行字体导入与精确字号。独立 Xvfb 检查主窗口／兼容 HUD 的保存软件渲染偏好、AWT 心跳、设置保存及正常退出通过，退出码 0。构建日志 `/tmp/voidmei-region-font-package.log`，运行日志 `/tmp/voidmei-region-font-smoke.log`，证据 `/tmp/voidmei-package-smoke-he8dam2e/`。

## 分区读数字体与旧飞行字体迁移（2026-09-12）

飞行／发动机分区新增独立标签与数字字体，可收起编辑器、预览、验证名称并分别应用；留空继承全局文字／HUD 数字字体，未安装字体有回退提示。字体作用于表格标签和数值（含单位），不改变标题或图形。旧飞行面板 `:font` 优先于历史 `flightInfoFontC` 设置项，独立勾选后应用到第一个飞行分区，包括本次补建的分区。

共享 JVM 523、JS 520、桌面单元 151、相关 GUI 25 项通过，无失败、错误或跳过。覆盖两分区实际文本字体、数字与标签隔离、恢复继承、缺失字体、旧 JSON 兼容、非法名称、迁移选择／优先级／撤回；截图 `desktop/build/hud-preview/region-reading-fonts.png` 已检查。最终日志 `/tmp/voidmei-region-font-final.log`。本轮未重建独立包，下方多列包不包含此字体增量；当前源码可用 Gradle 启动验证。

当前配置差异清点为 53 条记录、47 个不同 target，日志 `/tmp/voidmei-region-font-inventory.log`。Java 字号偏移同时影响部分窗口／图形尺寸，尚未直接转换为 KMP 字体缩放，详见 [剩余差异清单](legacy-setting-gaps.md)。

## 1–16 列读数与可选迁移（2026-09-12）

全局和飞行／发动机分区支持自定义 1–16 列，保留自动／单列／双列快捷选项，输入校验后应用。旧 `flightInfoColumn` 提供独立可选预览，只修改第一个飞行分区的列数，也支持本次补建的分区。旧动力窗口的 `hudColumns` 仍在差异清单中。

共享 JVM 519、JS 516、桌面单元 151 项通过，相关 GUI 28 项通过，无失败、错误或跳过。覆盖窄区域读数、多分区独立设置、非法输入、旧导入选择及撤回；三列截图已检查。日志 `/tmp/voidmei-multicolumn-validation.log`、`/tmp/voidmei-multicolumn-gui.log`。本轮只运行相关 GUI，不把下方历史完整 GUI 结果作为当前源码的完整回归。

当前导入器清点 `ui_layout.cfg` 得到 54 条未迁移记录、48 个不同 target；分类与复现方式见 [Java 配置剩余差异清单](legacy-setting-gaps.md)。

最新 Linux 独立包：`/tmp/voidmei-kmp-multicolumn`，实际输出 `/nix/store/a2paivi8v24i5sqgn910ydiqbscbnbpk-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-multicolumn/bin/voidmei-kotlin --no-hud` 可试用，包含本轮多列设置与前轮旧面板总开关格式兼容。隔离 Xvfb 验证主窗口／兼容 HUD 的保存软件渲染偏好、AWT 心跳、设置保存与正常退出通过，退出码 0。构建日志 `/tmp/voidmei-multicolumn-package.log`，运行日志 `/tmp/voidmei-multicolumn-smoke.log`，证据 `/tmp/voidmei-package-smoke-isk3ob8x/`。未扩大实际游戏或 Windows／macOS 的验收范围。

## 多列增量前的集成回归与差异清点（2026-09-12）

该轮生产源码完整 GUI 回归 142 类、338 项通过，无失败、错误或跳过，日志 `/tmp/voidmei-integrated-settings-gui.log`；使用隔离 Xvfb／xcompmgr 与软件渲染。此源码的共享 JVM 517、JS 514、桌面单元 151 项已在前一轮通过，本轮未重复执行。完整 GUI 结果覆盖近期各次设置增量的组合使用。

当前导入器读取仓库 `ui_layout.cfg` 得到 55 条未迁移记录、49 个不同 target；逐类核对见 [Java 配置剩余差异清单](legacy-setting-gaps.md)。其中包含操作入口、已迁移设置的部分未覆盖用途，以及仍待实现的结构和选择状态，不能当成缺失功能数量。新增只读清点工具已实际运行。本轮没有生产代码变更，也未重建包。

## 旧面板总开关格式增量（2026-09-12）

四类独立面板开关新增 `:switch-key`／`:visible` 格式支持，按 Java 的面板顺序和同面板显式行优先级解析，沿用显示开关的可选预览应用流程。未知面板开关列入未迁移报告。共享 JVM 517、JS 514、桌面单元 151、旧导入 GUI 18 项通过，最终日志 `/tmp/voidmei-panel-switch-final.log`。本轮未重建包；下方最新独立包不包含此保存格式增量。

## 旧配置导入撤回增量（2026-09-12）

导入后可撤回最近一次有变化的导入，恢复仍保持导入值的项目，保留后来改变的值。分区布局等复合设置整项比较；无变化或失败导入不覆盖恢复点，收起面板仍可撤回。共享 JVM 514、JS 511、桌面单元 151 项及旧导入 GUI 18 项通过。日志 `/tmp/voidmei-import-undo-tests.log`、`/tmp/voidmei-import-undo-gui.log`。


最新 Linux 独立包：`/tmp/voidmei-kmp-import-undo`，实际输出 `/nix/store/5mi7r70n8a3l5q1lbwbqa38p5gjj3844-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-import-undo/bin/voidmei-kotlin --no-hud` 可试用，包含导入撤回及姿态指北针。隔离 Xvfb 检查主窗口／兼容 HUD 保存的软件渲染偏好、AWT 心跳、设置保存与正常退出通过，退出码 0。构建日志 `/tmp/voidmei-import-undo-package.log`，运行日志 `/tmp/voidmei-import-undo-smoke.log`，运行证据 `/tmp/voidmei-package-smoke-ej24l_v2/`。这不扩大实际游戏和跨平台验收范围。

## 姿态图指北针增量（2026-09-12）

补齐旧 `attitudeIndicatorDisplayDirection` 的指北针叠加功能，支持设置、迁移、持久化及两种 HUD 布局。已有姿态分区时也可访问参考系与迎角极限线设置。共享 JVM 512、JS 509、桌面单元 151 项和相关 GUI 21 项通过；四个基本航向的实际像素方向验证通过，截图已检查。日志 `/tmp/voidmei-attitude-north-tests.log`、`/tmp/voidmei-attitude-north-gui.log`。此指北针增量已包含在上方导入撤回的独立包中。

## 旧像素坐标换算增量（2026-09-12）

修复旧像素坐标被误作比例而推到画布边缘的问题。导入预览按 Java 的逐轴 >2 规则识别像素，需要输入原屏幕坐标宽高才可迁移位置或补建；比例坐标不需要尺寸，其他设置仍可独立导入。共享 JVM 510、JS 507、桌面单元 151 项及相关 GUI 16 项通过，日志 `/tmp/voidmei-legacy-pixel-tests.log`、`/tmp/voidmei-legacy-pixel-gui.log`。


当前最新 Linux 独立包：`/tmp/voidmei-kmp-legacy-pixels`，实际输出 `/nix/store/ps8r5mxv1mlhgy5law96221dvygqx7mv-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-legacy-pixels/bin/voidmei-kotlin --no-hud` 可试用，包含旧像素坐标修复和分区搜索目录。隔离 Xvfb 验证主窗口与兼容 HUD 的保存软件渲染偏好、AWT 心跳、设置保存和正常退出（退出码 0）通过。构建／运行日志 `/tmp/voidmei-legacy-pixel-package.log`、`/tmp/voidmei-legacy-pixel-smoke.log`，证据 `/tmp/voidmei-package-smoke-ojlnyrxe/`。此验证不代表实际游戏、物理显示器或其他系统验收。

## 分区设置搜索与定位增量（2026-09-12）

当前源码新增可收起的分区目录，支持按类型、标题和编号搜索、跳转与返回；隐藏区域仍可查找，跳转保留尚未应用的几何输入。相关 GUI 回归通过，窄窗口截图已检查，日志 `/tmp/voidmei-region-directory-gui.log`。此 UI 增量已包含在上方旧像素坐标修复的独立包中。

## 独立面板显示开关增量（2026-09-12）

新增可选导入四类独立面板总开关：飞行、操纵面、姿态、机械化。默认保留当前显示状态，勾选后作用于第一个匹配分区，也可作用于本次补建的分区；不改全局 HUD 和 MiniHUD 姿态开关。共享 JVM 508、JS 505、桌面单元 151 项及相关 GUI 16 项通过。日志 `/tmp/voidmei-legacy-visibility-tests.log`、`/tmp/voidmei-legacy-visibility-gui.log`。此增量仍不包含旧窗口尺寸、背景透明度和完整结构迁移。


最新 Linux 独立包已构建：`/tmp/voidmei-kmp-legacy-visibility`，实际输出 `/nix/store/walfk421vl0rb96a863mx69qqz5jsf9w-voidmei-kotlin-2.0.0`。运行 `/tmp/voidmei-kmp-legacy-visibility/bin/voidmei-kotlin --no-hud` 可试用。包含位置补建和独立显示开关迁移。隔离 Xvfb 启动检查验证主窗口／兼容 HUD 使用保存的软件渲染偏好、AWT 心跳、设置保存及正常退出（退出码 0）；不代表真实游戏、物理 GPU 或跨平台验收。构建日志 `/tmp/voidmei-legacy-visibility-package.log`，运行日志 `/tmp/voidmei-legacy-visibility-smoke.log`，运行证据副本 `/tmp/voidmei-legacy-visibility-smoke-artifacts/`。

## 旧布局导入设置增量（2026-09-12）

本轮新增可选补建旧位置对应的缺失分区，覆盖飞行读数、姿态、操纵面和机械化。已有布局中使用 Kotlin 默认样式补建，预览列出位置和尺寸；不自动切换 HUD 模式、不创建重复类型，超过 32 个分区时禁止补建。未创建分区布局时提示先创建布局，旧尺寸、透明度及未对应窗口的转换仍未实现。

共享 JVM 505、JS 502、桌面单元 151 项通过；最终整行点击语义调整后，7 项旧设置相关 GUI 回归通过。日志 `/tmp/voidmei-legacy-create-tests.log` 和 `/tmp/voidmei-legacy-create-gui.log`。GUI 使用隔离 Xvfb／软件渲染；本轮未重建独立包，也未新增实机验收。此前整体目标的实机限制仍在，但不妨碍继续推进独立的功能和设置改进。

## 此前可运行交付点与实机验收限制

包含 macOS 前台检测增量的当前 Linux 包已构建：`/tmp/voidmei-kmp-current`，实际输出 `/nix/store/mbxdx5sm9dvynmvcbib2gaynnawnvnbx-voidmei-kotlin-2.0.0`。启动命令：`/tmp/voidmei-kmp-current/bin/voidmei-kotlin --no-hud`。主窗口与兼容 HUD 的保存渲染偏好、运行心跳和正常退出检查通过；日志 `/tmp/voidmei-current-package.log`、`/tmp/voidmei-current-package-smoke.log`，运行产物 `/tmp/voidmei-package-smoke-z5b2ojy3`。

目前无法证明完整实机验收：最新只读检查中，本机 `127.0.0.1:8111/state` 和 `/indicators` 都拒绝连接；当前执行环境为 Linux x86_64，没有可执行这些原生验收的 Windows／macOS 测试环境。此前多轮所列的同一环境限制仍未解除。下一步需要运行中的游戏试飞接口，以及所需目标系统的测试环境；不能以继续增加模拟测试或未经验证的原生分支代替这些证据。目标保持未完成，并因实机验收条件缺失而受阻。下面的实现限制仍保留，不视为已经解决。

## 此前统一验收快照（2026-09-12）

以下为后续增量前的历史快照；最新验证范围以顶部增量记录为准。该快照完整测试：JVM **503**、JS **500**、桌面单元 **148**、GUI **326**，共 **1477** 项，无失败、错误或跳过。不是将各次增量测试重复相加；统计来自本轮四个 Gradle 测试任务的 XML 报告。日志 `/tmp/voidmei-final-acceptance-tests.log`。

独立包 `/tmp/voidmei-kmp-acceptance` 已构建，实际输出 `/nix/store/q9cwjg94wh4wkz321h8025ji7j819r4g-voidmei-kotlin-2.0.0`。本机可执行 `/tmp/voidmei-kmp-acceptance/bin/voidmei-kotlin --no-hud`。此包包含当前 macOS 桥接源码编译结果，但仍为 Linux 包，不代表 macOS 运行验收。

同一个包通过两组隔离运行检查：

- 模拟飞行录制 **84** 个样本、**79** 个 WEP 上限估计，延迟期间历史保留，温度／高度表／滑油压力来源、分区 HUD、设置窗口和正常退出均通过。日志 `/tmp/voidmei-final-acceptance-recording.log`，原始产物 `/tmp/voidmei-package-smoke-ebf9ihtf`。
- 清除渲染覆盖后，保存的软件渲染偏好同时作用于主窗口与兼容 HUD，正常退出通过。日志 `/tmp/voidmei-final-acceptance-renderer.log`，原始产物 `/tmp/voidmei-package-smoke-x_psclxz`。

两组使用隔离 X11／Mesa 环境，第一组兼容 HUD 的 OpenGL 路径不构成物理 GPU 加速证明。本轮只读重查游戏 `/state`、`/indicators` 仍拒绝连接；实际游戏、Windows／macOS 及物理显示设备的验证状态不变。上述 `/tmp` 和 Nix 路径仅供本机复核，长期使用请按 README 从源码构建。

## 已有实现和本轮证据

| 范围 | 当前证据 | 证据边界 |
| --- | --- | --- |
| KMP 业务核心 | `core` 配置 JVM／JS；共享测试两端通过 | 没有浏览器或移动端应用 |
| 桌面与 HUD | Compose 桌面、十类分区、设置与预设；完整 GUI 136 类、326 项通过，无失败、错误或跳过 | 隔离 X11，未覆盖物理混合 DPI 和其他系统 |
| FM 数据与计算 | 五份固定哈希公开 FM 的解析、模型及曲线检查通过，含双引擎活塞和喷气机 | 不是全机型或实时飞行物理对照；部分格式仍降级 |
| 遥测与录制 | 已有模拟 HTTP、延迟恢复、配对 CSV、历史分析和回放验证 | 当前本机 8111 `/state`、`/indicators` 均拒绝连接，无法继续实战验收 |
| 配置迁移 | 核心设置、读数字段、软件渲染独立文件、语音目录及选择均有迁移与回归 | 支持可选迁移四类已有分区的位置；完整旧布局、尺寸与透明度尚未重建 |
| 交付 | 默认 Nix 为 Kotlin 独立包，最新包已构建；Kotlin 三平台 CI／候选工作流存在 | 不能用本地 Linux 构建证明 Windows／macOS 安装及运行 |

本轮日志：`/tmp/voidmei-migration-integrated-gui.log`、`/tmp/voidmei-migration-integrated-fm.log`。FM 输入及逐机型报告在 `/tmp/voidmei-public-fm/manifest.json` 和 `validation-report.json`，测试先校验原始字节哈希。临时路径仅供本机复核。

位置迁移增量验证：共享 JVM 503、JS 500、桌面单元 145 项通过，旧设置导入 GUI 回归 11 项通过。新包 `/tmp/voidmei-kmp-legacy-position` 构建及保存渲染偏好启动检查通过；日志 `/tmp/voidmei-position-tests.log`、`/tmp/voidmei-position-gui.log`、`/tmp/voidmei-position-package-smoke.log`。

## 仍需实现的明确缺口

- 原生 Wayland 游戏前台检测：[GameFocus.kt](../desktop/src/main/kotlin/voidmei/desktop/GameFocus.kt) 目前提供 Windows、独立 X11 和 macOS 路径，原生 Wayland 没有实现。
- FM 非对称方向阈值：[FlightModelParameters.kt](../core/src/commonMain/kotlin/voidmei/fm/FlightModelParameters.kt) 对不同值的方向数组明确返回未知。例如已校验的 F-14A 样本中 `ElevatorsEffectiveSpeed` 为 `[1801, 1800]`。需要确认方向含义并贯通解析、模型和显示／告警，不能直接取平均值或任选一项。
- 旧窗口布局迁移：[LegacySettingsPanel.kt](../desktop/src/main/kotlin/voidmei/desktop/LegacySettingsPanel.kt) 已支持按旧屏幕比例可选移动飞行、姿态、操纵面、机械化分区，并可在已有布局中补建缺少的对应类型，含预览及边界约束；MiniHUD、动力信息、引擎控制的结构、尺寸与透明度仍需手动设置，尚未提供完整旧布局转换。
- 默认稳定发行流程仍保留 Java 版；Kotlin 目前使用独立的候选流程。是否切换稳定发行应依据完整验收结果，不能将候选打包视为已发布。

## 仍需外部环境或数据的验收

- macOS 前台检测已实现：AppKit 主线程读取前台应用 PID／可执行路径，两次快照一致后才判断，原生调用失败时保持未知。尚未实机执行；入口 `VOIDMEI_TEST_ISOLATED_MACOS=1 ./gradlew :desktop:nativeMacFocusTest` 会前置自己的测试窗口并核对返回的 PID／文件路径，需在专用 macOS 测试桌面运行。本轮 150 项桌面单元测试、2 项 X11 前台检测 GUI 回归通过；日志 `/tmp/voidmei-macos-focus-regression.log`。前述统一验收包是这次前台检测增量前的快照。

- macOS HUD 鼠标穿透已接入 [MacPointerRegion.kt](../desktop/src/main/kotlin/voidmei/desktop/MacPointerRegion.kt)：AppKit 主线程调用、JDK 原生资源保护、原始策略恢复及启动参数已实现。Linux 上的编译、148 项桌面单元测试及已有两种 HUD 的 6 项原生点击、6 项窗口 GUI 回归通过；这不能验证 macOS 的原生 ABI、JDK 桥接和实际穿透。需在专用 macOS 桌面运行 `VOIDMEI_TEST_ISOLATED_MACOS=1 ./gradlew :desktop:nativeHudPointerTest --rerun-tasks`，再验收安装包。

- Windows／macOS 实际安装、启动、透明置顶、热键、窗口恢复、输入穿透及前台切换。尚未实现的项目应先完成实现，不能直接写成“仅待实测”。
- 实际游戏连续飞行、切机、重生、退出战斗及多引擎状态变化，并与原实现和原始遥测对照。当前本机接口未运行；已有用户对 SEP 和部分 HUD 行为的确认只覆盖相应问题。
- 物理多显示器、负坐标、混合 DPI 及显示器断开后的窗口恢复。
- 安装包在普通 Linux、Windows、macOS 上的可移植性，以及物理 GPU／驱动下的渲染与游戏并行性能。

完整历史、功能边界与原验收清单仍见 [迁移说明](kotlin-migration.md)。后续应按上述具体缺口推进，完成一项后补齐对应实现和验证证据，再更新状态。
