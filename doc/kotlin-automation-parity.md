# 自动化与启动行为迁移核对

以当前旧 Java 源码的调用路径为依据，区分普通用户可触发行为、编译期实验入口及未接入代码。此表不是整体迁移完成声明。

| 行为 | 旧版入口与实际效果 | Kotlin 当前状态 | 尚需完成 |
| --- | --- | --- | --- |
| 启动后自动进入游戏模式 | `ui_layout.cfg` 的 `autoStartGameMode`；`Controller` 仅首次启动读取，开启后调用 `start()`，跳过设置窗口。托盘恢复设置窗口不再次套用此偏好。 | 主程序启动即连接遥测；新增 startInTray 偏好与旧键导入，成功安装托盘后隐藏主窗口，可通过托盘恢复主窗口、切换 HUD 或正常退出。托盘不可用、配置错误或 --no-hud 恢复启动时显示主窗口。 | 验证各平台原生托盘可见性、菜单点击、恢复焦点及退出失败处理；不能仅凭配置测试认定完成。 |
| 启动自动录制 | 旧 `enableLogging`；与跳过设置窗口不是同一功能。 | `recordingAutoStart`、启动时录制和旧设置导入已实现。 | 保留与启动窗口策略的独立性。 |
| 录制状态反馈 | `Controller` 使用通知提示开始录制及日志保存路径。 | 主窗口显示录制状态、路径与错误；录制启动/写入失败现在复用托盘恢复主窗口流程。 | 成功开始/保存的原生通知尚未迁移，跨平台窗口恢复与通知效果待验证。 |
| 切出游戏隐藏浮窗 | `autoHideOnFocusLoss`，由 `Controller.openpad()` 启用。 | HUD 偏好、Windows 及 Linux X11 游戏前台检测已实现，未知前台时保持可见。X11 检查窗口的本机 PID 和 /proc 可执行文件；远程客户端、信息缺失、Wine 加载器等返回未知。 | Windows/Linux 真实游戏切换验证；Wayland 与 macOS 尚未接入。 |
| 襟翼保护按键 | `FlapsControl` 使用 `Robot` 发送 F 键，速度超过 300 km/h 且襟翼超过 20% 时尝试收起。当前源码只发现类定义及 `Controller.flc` 字段声明，没有实例化、初始化、启动或调用路径。 | 没有自动发送襟翼按键；已提供襟翼状态、模型限速与告警。 | 不把未接入的旧实验算法当作已发布功能或默认行为；如后续恢复该实验，需要独立设计和验证。 |
| FM 自动测量实验 | `Application.fmTesting=false`，当前源码没有赋值为 true 的路径。若修改源码启用，`Controller.openpad()` 启动 `AutoMeasure`，条件满足时发送高度 3000、速度 0 命令。迭代最大速度的调用已被注释。 | FM 数据分析和模型曲线已实现，但不发送这些试验控制命令。 | 记录为尚未移植的源码实验；不能声称旧版已具备可用的自动最高速度测量流程。 |

## 核对来源

- [旧控制器](../src/prog/Controller.java)：`flc` 声明、`openpad()`/关闭路径、初次启动与托盘恢复分支。
- [旧襟翼实验](../src/prog/FlapsControl.java)：按键及速度条件。
- [旧自动测量实验](../src/prog/AutoMeasure.java)：运行循环与已注释的速度迭代。
- [旧应用开关](../src/prog/Application.java)：`fmTesting` 默认 false。
- [旧设置模板](../ui_layout.cfg)：`autoStartGameMode` 的用户选项。
- [新版入口](../desktop/src/main/kotlin/voidmei/desktop/Main.kt)、[HUD 启动参数](../desktop/src/main/kotlin/voidmei/desktop/StartupHudOptions.kt)、[旧配置导入](../core/src/commonMain/kotlin/voidmei/config/LegacySettings.kt)。

后台启动与托盘恢复入口已实现首版，退出复用设置保存与录制排空流程；各平台原生托盘交互仍需验证。
