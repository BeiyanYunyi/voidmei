# Kotlin 重写验收状态

2026-09-12，根据当前工作区源码及本机执行结果核对。此页区分已有实现、尚未实现与尚未验证；不以测试总数代表完整替换。

## 最新可运行交付点与验收阻塞

包含 macOS 前台检测增量的当前 Linux 包已构建：`/tmp/voidmei-kmp-current`，实际输出 `/nix/store/mbxdx5sm9dvynmvcbib2gaynnawnvnbx-voidmei-kotlin-2.0.0`。启动命令：`/tmp/voidmei-kmp-current/bin/voidmei-kotlin --no-hud`。主窗口与兼容 HUD 的保存渲染偏好、运行心跳和正常退出检查通过；日志 `/tmp/voidmei-current-package.log`、`/tmp/voidmei-current-package-smoke.log`，运行产物 `/tmp/voidmei-package-smoke-z5b2ojy3`。

目前无法证明完整实机验收：最新只读检查中，本机 `127.0.0.1:8111/state` 和 `/indicators` 都拒绝连接；当前执行环境为 Linux x86_64，没有可执行这些原生验收的 Windows／macOS 测试环境。此前多轮所列的同一环境限制仍未解除。下一步需要运行中的游戏试飞接口，以及所需目标系统的测试环境；不能以继续增加模拟测试或未经验证的原生分支代替这些证据。目标保持未完成，并因实机验收条件缺失而受阻。下面的实现限制仍保留，不视为已经解决。

## 当前工作区统一验收（2026-09-12）

当前工作区完整测试：JVM **503**、JS **500**、桌面单元 **148**、GUI **326**，共 **1477** 项，无失败、错误或跳过。不是将各次增量测试重复相加；统计来自本轮四个 Gradle 测试任务的 XML 报告。日志 `/tmp/voidmei-final-acceptance-tests.log`。

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
- 旧窗口布局迁移：[LegacySettingsPanel.kt](../desktop/src/main/kotlin/voidmei/desktop/LegacySettingsPanel.kt) 已支持按旧屏幕比例可选移动已有飞行、姿态、操纵面、机械化分区，含预览及边界约束；MiniHUD、动力信息、引擎控制的结构、尺寸与透明度仍需手动设置，尚未提供完整旧布局转换。
- 默认稳定发行流程仍保留 Java 版；Kotlin 目前使用独立的候选流程。是否切换稳定发行应依据完整验收结果，不能将候选打包视为已发布。

## 仍需外部环境或数据的验收

- macOS 前台检测已实现：AppKit 主线程读取前台应用 PID／可执行路径，两次快照一致后才判断，原生调用失败时保持未知。尚未实机执行；入口 `VOIDMEI_TEST_ISOLATED_MACOS=1 ./gradlew :desktop:nativeMacFocusTest` 会前置自己的测试窗口并核对返回的 PID／文件路径，需在专用 macOS 测试桌面运行。本轮 150 项桌面单元测试、2 项 X11 前台检测 GUI 回归通过；日志 `/tmp/voidmei-macos-focus-regression.log`。前述统一验收包是这次前台检测增量前的快照。

- macOS HUD 鼠标穿透已接入 [MacPointerRegion.kt](../desktop/src/main/kotlin/voidmei/desktop/MacPointerRegion.kt)：AppKit 主线程调用、JDK 原生资源保护、原始策略恢复及启动参数已实现。Linux 上的编译、148 项桌面单元测试及已有两种 HUD 的 6 项原生点击、6 项窗口 GUI 回归通过；这不能验证 macOS 的原生 ABI、JDK 桥接和实际穿透。需在专用 macOS 桌面运行 `VOIDMEI_TEST_ISOLATED_MACOS=1 ./gradlew :desktop:nativeHudPointerTest --rerun-tasks`，再验收安装包。

- Windows／macOS 实际安装、启动、透明置顶、热键、窗口恢复、输入穿透及前台切换。尚未实现的项目应先完成实现，不能直接写成“仅待实测”。
- 实际游戏连续飞行、切机、重生、退出战斗及多引擎状态变化，并与原实现和原始遥测对照。当前本机接口未运行；已有用户对 SEP 和部分 HUD 行为的确认只覆盖相应问题。
- 物理多显示器、负坐标、混合 DPI 及显示器断开后的窗口恢复。
- 安装包在普通 Linux、Windows、macOS 上的可移植性，以及物理 GPU／驱动下的渲染与游戏并行性能。

完整历史、功能边界与原验收清单仍见 [迁移说明](kotlin-migration.md)。后续应按上述具体缺口推进，完成一项后补齐对应实现和验证证据，再更新状态。
