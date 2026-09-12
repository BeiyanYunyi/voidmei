# VoidMei — 战争雷霆遥测与 HUD

VoidMei 读取游戏本机 `8111` HTTP 接口，显示飞行、发动机、操纵面、地图和消息，并结合离线 FM 数据提供模型分析、语音告警及飞行记录。

当前默认入口为 **Kotlin Multiplatform + Compose Desktop**，桌面运行时使用 **JDK 21**。重写仍处于迁移开发阶段，尚未完成全部平台与真实游戏验收。Java 8 / WebLaF 版本保留用于回退，见 [旧版指南](doc/legacy-java.md)。

已验证范围、尚未实现功能及需要实测的项目见 [当前验收状态](doc/kotlin-acceptance.md)。

## 运行 Kotlin 版

NixOS / Linux x86_64：

```bash
nix run path:. -- --no-hud
# 开启兼容透明 HUD
nix run path:. -- --hud --compatible-hud
# 构建独立包
nix build path:.
```

默认包包含运行时，启动无需 Gradle 或可写源码目录；首次构建可能需要下载依赖。`--no-hud` 是关闭 HUD 的恢复入口，进入主窗口后可重新开启。FM 游戏数据需另行提供，不随包分发。

从源码开发（JDK 21）：

```bash
./gradlew :desktop:run --args=--no-hud
./gradlew :core:allTests :desktop:test
./gradlew :desktop:packageDistributionForCurrentOS
```

Nix 开发环境（提供 JDK、Gradle 和 Node）：

```bash
nix develop path:.
gradle -Pvoidmei.systemNode=true :core:allTests :desktop:test
gradle :desktop:run --args=--no-hud
```

无游戏时可在另一终端执行 `python script/mock_8111.py serve` 使用模拟遥测。服务器默认 `http://127.0.0.1:8111`，可在界面修改或通过 `VOIDMEI_ENDPOINT` 设置。完整的 HUD 设置、旧配置导入、设置备份、数据目录和录制操作见 [Kotlin 版试用步骤](doc/kotlin-quick-start.md)。

## 实现与验证范围

| 模块 | 职责 |
| --- | --- |
| `core/src/commonMain` | JVM／JS 共享的遥测解析、连接状态、飞行计算、FM 分析、告警和配置逻辑 |
| `desktop/src/main` | Compose 窗口与 HUD、HTTP、文件、语音、托盘及平台原生窗口集成 |
| `script/` | 模拟遥测、飞行采集、包运行与隔离桌面测试 |
| `nix/` | 固定依赖的 Linux 独立应用包 |
| `src/` | 原 Java 8 实现，供迁移对照 |

KMP 核心目前以 JVM 和 JS/Node 目标验证；交互应用为 Compose Desktop，并不提供浏览器或移动端应用。Kotlin 构建不依赖旧 Java / WebLaF 源码。

窗口使用 Compose/Skia 后端，主窗口提供实际渲染信息；使用 Kotlin 不代表所有机器都能启用 GPU。Linux 默认兼容 HUD 支持 OpenGL，软件渲染可用于恢复。单窗口分区 HUD 支持独立布局、字段、透明度及预设，操作见 [单窗口 HUD](doc/single-window-hud.md)。

Linux 已有共享逻辑、桌面、隔离 X11 GUI、透明合成与独立包验证；实际游戏中的部分问题也已有用户确认。Windows／macOS 原生行为、物理多屏及混合 DPI、完整真实飞行计算对照仍需验收。X11 的穿透与热键结果不代表原生 Wayland 支持。当前能力、历史修复及完整验收清单见 [迁移说明](doc/kotlin-migration.md)。

三平台安装包通过 Gradle 和 CI 构建，发布候选生成方式见 [Kotlin 发布候选](doc/kotlin-preview-release.md)。构建成功不等于各平台桌面验收完成。

## 旧版回退

```bash
nix run path:.#legacy-java
nix develop path:.#legacy-java
```

Java 8 编译、Scoop 安装、Eclipse/VSCode 和 Wine 操作已移至 [旧 Java 版指南](doc/legacy-java.md)。
