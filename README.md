# VoidMei - 战争雷霆 8111 遥测与 HUD

正在迁移到 **Kotlin Multiplatform + Compose Desktop**。新代码位于 `core/` 和 `desktop/`，
当前提供遥测主窗口与 HUD，完整替换尚未完成。运行方式、迁移范围和验收清单见
[Kotlin 迁移说明](doc/kotlin-migration.md)。首次试用可按 [Kotlin 版试用步骤](doc/kotlin-quick-start.md)
连接模拟遥测、调整 HUD、导入旧设置并录制。下文是原 Java 版本的构建与使用方式。

NixOS 上可在源码目录运行 `nix run path:.#kotlin -- --no-hud` 启动 Kotlin 开发版，
再从界面开启 HUD。该开发入口会先用 Gradle 构建应用。
默认 `nix run path:.` 与 `nix build path:.` 现使用独立 Kotlin 包，`nix develop path:.` 提供 JDK 21 开发环境。
独立 Kotlin 包可用 `nix run path:.#kotlin-offline -- --no-hud` 运行，启动时无需 Gradle 或可写源码目录。
首次构建仍需下载固定哈希的依赖；运行与迁移限制见上方说明。
旧 Java 8 版可通过 `nix run path:.#legacy-java` 启动，旧开发环境为 `nix develop path:.#legacy-java`。
Kotlin 安装包的三平台发布候选与校验流程见 [Kotlin 发布候选](doc/kotlin-preview-release.md)。
Linux 新配置默认使用兼容 HUD 显示路径，可使用 OpenGL；已有配置保留原选择。
旧 Kotlin 配置若尚未保存过该选项，也会采用当前平台默认值。
若开启 HUD 后无响应，可用 `--no-hud` 恢复启动，再启用“HUD 兼容显示”。
Linux/Windows 的纵向 HUD 提供“HUD 鼠标穿透”开关，默认关闭。开启后点击会交给下方窗口；
需回到主窗口关闭穿透后才能拖动或点击纵向 HUD。单窗口分区模式始终穿透，在设置预览中排列区域，
支持每区独立背景和内容透明度，见 [单窗口分区操作](doc/kotlin-quick-start.md#单窗口分区布局)。Linux 的 X11 输入测试已通过；
Windows 实现尚待实际桌面验证，macOS 尚未支持。
Windows 另提供“切出游戏时隐藏 HUD”，默认关闭；检测失败时保持显示，
自动隐藏不停止遥测或录制。这项前台检测也仍待 Windows 实机验证。

# 工作原理
- 通过HTTP/GET请求读取127.0.01:8111端口中的飞行状态(state)以及飞行仪表(indicators)数据
- 解析离线拆包的气动模型文件(FM blkx)
- 处理/计算上述信息,以图形界面的形式呈现给用户


# 编译方式1: 统一构建脚本 (推荐)
**需确保 JDK 1.8 与 git-bash (Windows) 环境**
- clone 本仓库后, 将 FM 数据放入 `data/` (运行 `python script/build.py fmdata` 从游戏客户端解包生成, 或从 release 包中复制)
```bash
python script/build.py compile   # 编译 src/ → bin/
python script/build.py run       # 本地运行 (classpath 直跑, 免打 jar)
python script/build.py test      # 运行单元测试
python script/build.py dist      # 组装完整分发包 → dist/VoidMei_v*.zip
java -jar VoidMei.jar       # 本地运行 (项目根即运行目录, 需先 jar)
```

# 编译方式2: 命令行手动编译
```bash
mkdir bin
javac -encoding UTF-8 -d bin -classpath dep/* src/prog/* src/parser/* src/ui/*
jar -cvfm VoidMei.jar MANIFEST.MF -C ./bin .
java -jar VoidMei.jar
# 使用launch4j打包为exe, 确保launch4j环境目录已配置
launch4jc ./script/voidmeil4j.xml
```

# 编译方式3: Eclipse IDE
- 使用eclipse导入工程,程序入口设置为app.java中的main函数
- 设置jdk/jre版本为1.8 (java 8)
- 导入外部UI库 weblaf-complete-1.29.jar
- 下载release版本,将其他缺失的资源文件复制到本目录
- 运行、调试或导出jar文件

# 编译方式4: VSCode IDE
- 安装java插件
- 下载release版本,将其他缺失的资源文件复制到本目录
- 打开本目录,选择app.java并点击运行或调试
- 在JAVA PROJECTS选项下点击export jar可导出可执行的jar文件

# 代码结构说明
由于编程过程比较随意,目前代码结构与变量命名比较混乱,后面有时间会调整
主要代码结构描述如下:
- src/prog/app.java - 程序入口
- src/prog/controller.java - 程序状态转换控制
- src/prog/service.java - HTTP数据请求与数据处理线程,
- src/prog/uiThread.java - UI绘制线程
- src/parser - state/indicator/blkx等解析器代码
- src/ui - 各ui界面的绘制代码
- src/ui/mainform.java - 主界面
- src/ui/minimalHUD.java - 最小HUD界面
- src/ui/flightInfo.java - 飞行状态信息界面
- src/ui/engineInfo.java - 引擎状态信息界面
- src/ui/stickValue.java - 飞行控制信息界面
- src/ui/engineControl.java - 发动机控制信息界面

# 执行环境
- 安装 Jave Runtime Environment 1.8(jre 1.8) 即可

## Windows命令行模式安装VoidMei
打开非管理员模式的终端[按下WIN+R-输入cmd-按下回车]，跳出终端窗口后输入以下命令

先安装scoop(如果已安装可跳过)
```
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex
```

设置代理(如果网络无问题可以直接跳过)
```
scoop config proxy [ip:port]
```

安装git(如果已安装可跳过)
```
scoop install git
```

添加@Lustra-Fs大佬提供的bucket
```
scoop bucket add Lutra-Fs_scoop-bucket https://github.com/Lutra-Fs/scoop-bucket
```

安装Voidmei，安装完成后开始菜单中应该能看到VoidMei可执行文件
```
scoop install Lutra-Fs_scoop-bucket/voidmei
```

版本升级请用该命令
```
scoop update voidmei
```

## 旧 Java 版 Linux 原生运行（Nix flake）

本节仅用于旧版回退和维护。默认 Nix 入口现为 Kotlin，日常运行见上方 [Kotlin 版试用步骤](doc/kotlin-quick-start.md)。

通过显式 `#legacy-java` 提供 `x86_64-linux` 的 Java 8 构建、运行入口和开发环境。需要启用 Nix 的 `nix-command`、`flakes` 功能，以及可用的 X11 显示服务（Wayland 桌面需 XWayland）。

```bash
nix build path:.#legacy-java     # 编译旧版、运行单元测试，产物在 result/
nix run path:.#legacy-java       # 启动旧 Java 图形界面
nix develop path:.#legacy-java   # 进入 JDK 8 + Python 3 开发环境
python script/build.py compile
python script/build.py test
python script/build.py run       # 以仓库根目录为运行目录
```

`path:.` 会读取当前工作区。使用 Git flake 简写时仍需保留 `#legacy-java`，例如 `nix run .#legacy-java`；省略该选择器会使用默认 Kotlin 入口。

旧版打包入口默认在 `${XDG_DATA_HOME:-$HOME/.local/share}/voidmei` 保存资源和运行数据，首次运行复制自带资源，后续启动保留已有文件。可用 `VOIDMEI_HOME` 指定其他目录：

```bash
VOIDMEI_HOME="$PWD" nix run path:.#legacy-java  # 旧版使用当前仓库的资源、data/ 和配置
```

FM 游戏数据不随 flake 打包。请将解包生成或发行包中的 `data/` 放入运行目录；连接游戏时需要本机 `8111` 端口可用。缺少 FM 数据时无法验证完整气动模型功能。Linux 的游戏前台检测当前会直接返回 true；Wayland 原生窗口中的全局热键受 XWayland 限制。

已在 Linux x86_64 / XWayland 下验证构建、7 组单元测试、主窗口显示和页面切换，原生热键库注册成功。构建时预解包 JNativeHook，避免向只读 Nix store 写入；Linux 下还修正了 WebLaF 非活动窗口的零尺寸阴影异常。依赖真实 FM 数据的 4 组测试因缺少数据而跳过，尚未验证实际游戏联动。部分系统字体配置可能产生 Fontconfig 警告，本次运行中文显示正常。

## 旧 Java 版 Linux 执行环境配置（Wine）
VoidMei可使用Linux wine执行(测试环境Fedora 35, GNOME 41.7, Wine 7.10),执行步骤如下: 
- winecfg 兼容性设置为win10 
- 安装jre8, 执行wine jre-8uXXX-windows-x64.exe /s
- 运行VoidMei

### 准备编译环境

``` bash
# 下载voidmei源码
pushd ~/project/
git clone git@github.com:matrixsukhoi/voidmei.git
popd

# 准备资源文件
pushd ~/downloads/
# download VoidMei_v1_573.zip to ~/downloads/voidmei_v1_573.zip from github release
mkdir -p voidmei
cp VoidMei_v1_573.zip voidmei/
cd voidmei
unzip VoidMei_v1_573.zip
popd

# 准备wine和java环境
pushd ~/downloads/
# download jre8 zip from https://www.azul.com/downloads/?version=java-8-lts&os=windows&architecture=x86-64-bit&package=jre
unzip zulu8.90.0.19-ca-jre8.0.472-win_x64.zip
cp -r ~/Downloads/zulu/zulu8.90.0.19-ca-jre8.0.472-win_x64/ ./
WINEPREFIX=$(pwd)/.wine_voidmei winetricks corefonts fakechinese cjkfonts
## 运行voidmei
WINEPREFIX=$(pwd)/.wine_voidmei wine ./zulu8.90.0.19-ca-jre8.0.472-win_x64/bin/java.exe -jar VoidMei.jar
popd

# 准备其他工具
sudo pacman -Ss launch4j

```

### 本地编译voidmei并运行

项目根目录即运行工作区 (data/fonts/voice 等资源就位后):

``` bash
pushd ~/project/voidmei
python script/build.py jar
WINEPREFIX=~/downloads/.wine_voidmei wine ~/downloads/zulu8.90.0.19-ca-jre8.0.472-win_x64/bin/java.exe -jar VoidMei.jar
popd

```
